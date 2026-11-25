package dev.isotopelib.sim.engine;

public final class PrimaryLoop {
    private final PlantConfig cfg;

    private double mass = 1.0;
    private double pressure;
    private final double[] pump;
    private final boolean[] pumpOn;
    private double tHot, tCold;
    private double flow = 1.0;
    private double porv;
    private boolean porvStuck;
    private boolean blockValveClosed;
    private double breakArea;
    private double accumulatorLeft;
    private double lastTavg;
    private double massFlowOut, massFlowIn;
    private double h2Primary;
    private double steamToContainmentKgS;
    private boolean hpi, lpi, accDischarging;
    private boolean heaterOn, sprayOn, safetyOpen;
    private double injectedBoronPpm;

    public PrimaryLoop(PlantConfig cfg) {
        this.cfg = cfg;
        this.pump = new double[cfg.pumps];
        this.pumpOn = new boolean[cfg.pumps];
        java.util.Arrays.fill(pump, 1.0);
        java.util.Arrays.fill(pumpOn, true);
        pressure = cfg.ratedPressure;
        tHot = cfg.ratedCoolantTemp + 13.0;
        tCold = cfg.inletTemp;
        accumulatorLeft = cfg.accumulatorVolume;
        lastTavg = 0.5 * (tHot + tCold);
    }

    public void step(double dt, double coreOutletK, double qSteamGen, boolean ac, boolean offsite, boolean si, double containmentBar,
                     dev.isotopelib.sim.engine.Rng rng, double boost) {
        for (int i = 0; i < pump.length; i++) {
            boolean run = pumpOn[i] && offsite;
            if (run)
                pump[i] = Math.min(1.0, pump[i] + dt / 5.0);
            else
                pump[i] = Math.max(0.0, pump[i] * Math.exp(-dt / cfg.pumpCoastdownSeconds));
        }
        double pumpFlow = 0;
        for (double v : pump)
            pumpFlow += v;
        pumpFlow /= pump.length;

        tHot += (coreOutletK - tHot) * Math.min(1.0, dt / 2.0);
        double invFactor = clamp(mass / 0.7, 0.0, 1.0);
        double dT = Math.max(0.0, tHot - tCold);
        double natural = cfg.naturalCirculation * Math.sqrt(clamp(dT / 25.0, 0.0, 1.5)) * invFactor;
        flow = Math.max(pumpFlow * invFactor, natural);
        double tColdTarget;
        if (flow < 0.01)
            tColdTarget = tHot;
        else
            tColdTarget = Math.max(cfg.ambientTemp, tHot - Math.min(60.0, qSteamGen / flow * 25.0));

        double mix = clamp(massFlowIn * 25.0 / Math.max(flow, 0.05), 0.0, 0.6);
        tColdTarget = (1.0 - mix) * tColdTarget + mix * (cfg.ambientTemp + 30.0);
        tCold += (tColdTarget - tCold) * Math.min(1.0, dt / 8.0);

        double tAvg = 0.5 * (tHot + tCold);
        double dTavg = tAvg - lastTavg;
        lastTavg = tAvg;
        double dp = cfg.swellBarPerKelvin * dTavg;
        heaterOn = ac && pressure < cfg.heaterOnBar && mass > 0.5;
        sprayOn = pressure > cfg.sprayOnBar && pumpFlow > 0.3;
        if (heaterOn)
            dp += cfg.heaterBarPerSecond * dt;
        if (sprayOn)
            dp -= cfg.sprayBarPerSecond * dt;

        double tSat = Steam.satTemp(pressure);
        double hotest = Math.max(tHot, coreOutletK);
        if (hotest > tSat)
            dp += Math.min(8.0, cfg.flashBarPerKelvinSecond * (hotest - tSat)) * dt;

        if (!porvStuck || blockValveClosed) {
            if (pressure > cfg.poRvOpenBar && porv < 0.5 && !blockValveClosed) {
                porv = 1.0;
                if (rng != null && rng.chance(cfg.poRvStuckChance))
                    porvStuck = true;
            } else if (pressure < cfg.poRvCloseBar && !porvStuck)
                porv = 0.0;
        }
        if (blockValveClosed)
            porv = 0.0;
        safetyOpen = pressure > cfg.safetyValveBar;
        double safety = safetyOpen ? clamp((pressure - cfg.safetyValveBar) / 6.0, 0.0, 1.0) : 0.0;
        double backPressure = Math.max(1.0, containmentBar);
        double pr = Math.sqrt(Math.max(0.0, pressure - backPressure) / (cfg.ratedPressure - 1.0));
        double outValve = (porv + safety) * cfg.poRvMassFraction * pr * Math.max(1.0, boost);
        double outBreak = breakArea * cfg.breakMassFraction * pr;

        double steamShare = clamp((hotest - tSat) / 30.0 + (mass < 0.7 ? 0.6 : 0.0), 0.0, 1.0);
        double outflow = (outValve + outBreak) * (1.0 - 0.6 * steamShare);
        dp -= (outValve * 8000.0 + outBreak * 3000.0) * dt * 0.01 * Math.max(0.3, pressure / cfg.ratedPressure);

        hpi = si && ac && pressure < cfg.hpiShutoffBar;
        lpi = si && ac && pressure < cfg.lpiOnBar;
        double inHpi = hpi ? cfg.hpiMassFraction * Math.sqrt(clamp(1.0 - pressure / cfg.hpiShutoffBar, 0.0, 1.0)) : 0.0;
        double inLpi = lpi ? cfg.lpiMassFraction : 0.0;
        double inAcc = 0.0;
        accDischarging = pressure < cfg.accumulatorOnBar && accumulatorLeft > 0;
        if (accDischarging) {
            inAcc = cfg.accumulatorMassFraction;
            accumulatorLeft = Math.max(0.0, accumulatorLeft - inAcc * dt);
        }
        double inflow = inHpi + inLpi + inAcc;
        injectedBoronPpm = inflow > 0 ? cfg.rwstBoronPpm : 0.0;

        double dM = (inflow - outflow) * dt;
        mass = clamp(mass + dM, 0.0, 1.05);
        dp += cfg.inventoryPressureBar * dM;
        massFlowOut = outflow;
        massFlowIn = inflow;

        pressure += dp;

        if (mass < 0.05)
            pressure += (backPressure - pressure) * Math.min(1.0, dt / 5.0);
        pressure = clamp(pressure, backPressure, 220.0);

        steamToContainmentKgS = (outValve + outBreak) * 250.0 * (0.5 + 0.5 * steamShare);
    }

    public void setPumps(boolean on) {
        java.util.Arrays.fill(pumpOn, on);
    }

    public void tripPump(int i) {
        if (i >= 0 && i < pumpOn.length)
            pumpOn[i] = false;
    }

    public void closeBlockValve() {
        blockValveClosed = true;
    }

    public void openBlockValve() {
        blockValveClosed = false;
    }

    public void stickPorvOpen() {
        porvStuck = true;
        porv = 1.0;
    }

    public void unstickPorv() {
        porvStuck = false;
    }

    public void setBreakArea(double area) {
        breakArea = Math.max(0.0, area);
    }

    public void setMass(double m) {
        mass = clamp(m, 0.0, 1.05);
    }

    public void setPressure(double p) {
        pressure = clamp(p, 1.0, 220.0);
    }

    public void refillAccumulators() {
        accumulatorLeft = cfg.accumulatorVolume;
    }

    public void addHydrogen(double kg) {
        h2Primary += kg;
    }

    public double takeHydrogen(double dt) {
        double share = Math.min(1.0, massFlowOut * dt * 60.0 + (porv > 0 ? 0.0 : 0.0));
        double out = h2Primary * share;
        h2Primary -= out;
        return out;
    }

    public double mass() { return mass; }
    public double pressure() { return pressure; }
    public double flow() { return flow; }
    public double tHot() { return tHot; }
    public double tCold() { return tCold; }
    public double tAvg() { return 0.5 * (tHot + tCold); }
    public double coreLevel() { return clamp((mass - cfg.coreBottomMass) / (cfg.coreTopMass - cfg.coreBottomMass), 0.0, 1.0); }
    public boolean porvOpen() { return porv > 0.5; }
    public boolean porvStuck() { return porvStuck; }
    public boolean blockValveClosed() { return blockValveClosed; }
    public double breakArea() { return breakArea; }
    public double dischargeKgS() { return steamToContainmentKgS; }
    public double outflow() { return massFlowOut; }
    public double inflow() { return massFlowIn; }
    public double accumulatorLeft() { return accumulatorLeft; }
    public double injectedBoronPpm() { return injectedBoronPpm; }
    public double hydrogenPrimaryKg() { return h2Primary; }
    public boolean hpi() { return hpi; }
    public boolean lpi() { return lpi; }
    public boolean heater() { return heaterOn; }
    public boolean spray() { return sprayOn; }
    public int pumpsRunning() {
        int n = 0;
        for (double v : pump)
            if (v > 0.5)
                n++;
        return n;
    }
    public double pumpFlowFraction() {
        double s = 0;
        for (double v : pump)
            s += v;
        return s / pump.length;
    }

    public void save(java.util.List<Double> out) {
        out.add(mass); out.add(pressure); out.add(tHot); out.add(tCold); out.add(flow); out.add(porv);
        out.add(porvStuck ? 1.0 : 0.0); out.add(blockValveClosed ? 1.0 : 0.0); out.add(breakArea); out.add(accumulatorLeft);
        out.add(lastTavg); out.add(h2Primary); out.add(massFlowOut); out.add(massFlowIn);
        for (int i = 0; i < pump.length; i++) {
            out.add(pump[i]);
            out.add(pumpOn[i] ? 1.0 : 0.0);
        }
    }

    public int load(double[] a, int k) {
        mass = a[k++]; pressure = a[k++]; tHot = a[k++]; tCold = a[k++]; flow = a[k++]; porv = a[k++];
        porvStuck = a[k++] > 0.5; blockValveClosed = a[k++] > 0.5; breakArea = a[k++]; accumulatorLeft = a[k++];
        lastTavg = a[k++]; h2Primary = a[k++]; massFlowOut = a[k++]; massFlowIn = a[k++];
        for (int i = 0; i < pump.length; i++) {
            pump[i] = a[k++];
            pumpOn[i] = a[k++] > 0.5;
        }
        return k;
    }

    public int saveSize() {
        return 14 + 2 * pump.length;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
