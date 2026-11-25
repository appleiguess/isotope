package dev.isotopelib.sim;

public final class ReactorSim {
    public final SimConfig cfg;

    private double rodTargetRegulating = 1.0;
    private boolean scram;

    private double coolantFlow = 1.0;

    private double heatSink = 1.0;

    private double fuelReactivity;

    private double bonusReactivity;

    private double rodTargetShutdown = 0.0;
    private double rodRegulating = 1.0;
    private double rodShutdown = 0.0;
    private double precursor = 1e-7;
    private double power = 1e-7;
    private final double[] decay;
    private double iodine, xenon, samarium;
    private double fuelTemp, coolantTemp;
    private double hydrogen;
    private double cladIntegrity = 1.0;

    private double inventory = 1.0;
    private double lastReactivity;
    private boolean prompt;
    private double rodWorthMultiplier = 1.0;
    private double timeSinceScram = 1e9;

    public ReactorSim(SimConfig cfg) {
        this.cfg = cfg;
        this.decay = new double[cfg.decayTaus.length];
        this.fuelReactivity = cfg.fuelExcessDollars;
        this.fuelTemp = cfg.ambientTemp;
        this.coolantTemp = cfg.ambientTemp;
    }

    public void setRodTarget(double regulatingInsertion) {
        this.rodTargetRegulating = clamp(regulatingInsertion, 0, 1);
    }

    public void setShutdownTarget(double ins) {
        this.rodTargetShutdown = clamp(ins, 0, 1);
    }

    public void setScram(boolean on) {
        if (on && !scram)
            timeSinceScram = 0;
        scram = on;
    }

    public void setCoolantFlow(double flow) {
        this.coolantFlow = clamp(flow, 0, 1);
    }

    public void setHeatSink(double sink) {
        this.heatSink = clamp(sink, 0, 1);
    }

    public void setFuelReactivity(double dollars) {
        this.fuelReactivity = dollars;
    }

    public void setBonusReactivity(double dollars) {
        this.bonusReactivity = dollars;
    }

    public void setRodsInstant(double regulating, double shutdown) {
        this.rodRegulating = clamp(regulating, 0, 1);
        this.rodShutdown = clamp(shutdown, 0, 1);
    }

    public void setTemperaturesInstant(double fuel, double coolant) {
        this.fuelTemp = fuel;
        this.coolantTemp = coolant;
    }

    public void startAtRatedPower(double p) {
        double sumA = 0;
        for (double d : cfg.decayAmplitudes)
            sumA += d;
        double pf = p / (1.0 + sumA);
        this.power = pf;
        this.precursor = pf;
        this.coolantTemp = cfg.inletTemp + p * (cfg.ratedCoolantTemp - cfg.inletTemp);
        this.fuelTemp = coolantTemp + p * (cfg.ratedFuelTemp - cfg.ratedCoolantTemp);
        double lamX = Math.log(2) / (cfg.xenonHalfLifeHours * 3600.0) * cfg.timeCompression;
        double b = cfg.xenonBurnRatio * lamX;
        this.iodine = pf;
        this.xenon = (lamX + b) * pf / (lamX + b * pf);
        this.samarium = pf > 0.01 ? 1.0 : 0.0;
        this.inventory = 1.0;
        for (int i = 0; i < decay.length; i++)
            decay[i] = cfg.decayAmplitudes[i] * pf;

        double lo = 0, hi = 1;
        for (int it = 0; it < 60; it++) {
            double mid = (lo + hi) / 2;
            this.rodRegulating = mid;
            if (reactivity() > 0)
                lo = mid;
            else
                hi = mid;
        }
        this.rodTargetRegulating = this.rodRegulating;
    }

    private static double rodCurve(double x) {
        return x * x * (3 - 2 * x);
    }

    public double voidPercent() {
        return 100.0 * Math.max(clamp((coolantTemp - cfg.boilTemp) / cfg.boilSpan, 0, 1), 1.0 - inventory);
    }

    public double reactivity() {
        double rho = fuelReactivity + bonusReactivity - cfg.shimDollars;
        rho -= rodCurve(rodRegulating) * cfg.regulatingRodWorth * rodWorthMultiplier;
        rho -= rodCurve(rodShutdown) * cfg.shutdownRodWorth * rodWorthMultiplier;
        rho += cfg.dopplerPerKelvin * (Math.max(fuelTemp, cfg.minFeedbackFuelTemp) - cfg.refFuelTemp);
        rho += cfg.coolantTempPerKelvin * (Math.max(coolantTemp, cfg.inletTemp) - cfg.refCoolantTemp);
        rho += cfg.voidPerPercent * voidPercent();
        rho -= cfg.xenonEquilibriumWorth * xenon;
        rho -= cfg.samariumEquilibriumWorth * samarium;
        if (cfg.positiveScramDollars > 0 && rodShutdown > 0 && rodShutdown < 0.6)
            rho += cfg.positiveScramDollars * Math.sin(Math.PI * rodShutdown / 0.6);
        return rho;
    }

    public void step(double dt) {
        int n = (int) Math.ceil(dt / 0.05);
        double h = dt / n;
        for (int i = 0; i < n; i++)
            substep(h);
    }

    private void substep(double dt) {
        moveRods(dt);
        double rho = reactivity();
        lastReactivity = rho;
        kinetics(rho, dt);
        decayHeat(dt);
        xenonAndSamarium(dt);
        thermal(dt);
        damage(dt);
    }

    private void moveRods(double dt) {
        timeSinceScram += dt;
        if (scram) {
            rodShutdown = Math.min(1.0, rodShutdown + dt / cfg.scramTime);
            rodRegulating = Math.min(1.0, rodRegulating + dt / cfg.scramTime);
            return;
        }

        double ds = rodTargetShutdown - rodShutdown;
        double maxS = dt * cfg.rodSpeed;
        rodShutdown += Math.abs(ds) <= maxS ? ds : Math.signum(ds) * maxS;
        double d = rodTargetRegulating - rodRegulating;
        double max = dt * cfg.rodSpeed;
        rodRegulating += Math.abs(d) <= max ? d : Math.signum(d) * max;
    }

    private void kinetics(double rho, double dt) {
        if (rho < 1.0) {
            prompt = false;
            double r = Math.min(rho, 0.98);
            precursor *= Math.exp(cfg.lambdaDelayed * r / (1.0 - r) * dt);
            precursor = Math.max(precursor, cfg.sourceLevel);
            power = Math.max(cfg.sourceLevel, precursor / (1.0 - r));
        } else {
            prompt = true;
            power = Math.min(cfg.maxPromptPower, power * Math.exp((rho - 1.0 + 0.5) / cfg.promptTau * dt));
            precursor = Math.max(cfg.sourceLevel, power * 0.02);
        }
    }

    private void decayHeat(double dt) {
        double c = cfg.timeCompression;
        for (int i = 0; i < decay.length; i++) {
            double tau = cfg.decayTaus[i] / c;

            double tgt = cfg.decayAmplitudes[i] * Math.min(power, 1.0);
            decay[i] += (tgt - decay[i]) * (1.0 - Math.exp(-dt / tau));
        }
    }

    public double decayHeatFraction() {
        double s = 0;
        for (double d : decay)
            s += d;
        return s;
    }

    private void xenonAndSamarium(double dt) {
        double c = cfg.timeCompression;
        double lamI = Math.log(2) / (cfg.iodineHalfLifeHours * 3600.0) * c;
        double lamX = Math.log(2) / (cfg.xenonHalfLifeHours * 3600.0) * c;
        double b = cfg.xenonBurnRatio * lamX;
        double a = lamX + b;
        double flux = Math.min(power, 1.5);
        double di = lamI * (flux - iodine);
        double dx = a * (cfg.xenonDirectShare * flux + (1 - cfg.xenonDirectShare) * iodine) - lamX * xenon - b * flux * xenon;
        iodine = Math.max(0, iodine + di * dt);
        xenon = Math.max(0, xenon + dx * dt);
        double tauSm = cfg.samariumTauHours * 3600.0 / c;
        samarium = clamp(samarium + flux * (1.0 - samarium) / tauSm * dt, 0, 1);
    }

    private void thermal(double dt) {
        double c = cfg.timeCompression;
        double h = 1.0 / (cfg.ratedFuelTemp - cfg.ratedCoolantTemp);
        double k = 1.0 / (cfg.ratedCoolantTemp - cfg.inletTemp);
        double cf = cfg.fuelTimeConstant * h;
        double cc = cfg.coolantTimeConstant * (h + k);
        double flow = Math.max(cfg.naturalCirculation, coolantFlow);
        double qDecay = decayHeatFraction();

        double covered = clamp((inventory - 0.3) / 0.4, 0.0, 1.0);

        double qDecayEffective = qDecay * (1.0 + (c - 1.0) * (1.0 - covered));
        double qOxidation = fuelTemp > cfg.oxidationK ? 0.0006 * (fuelTemp - cfg.oxidationK) * (1.0 - hydrogenSaturation()) : 0.0;
        double toCoolant = h * (fuelTemp - coolantTemp) * Math.max(0.03, covered);
        double removed = k * flow * heatSink * Math.max(0, coolantTemp - cfg.inletTemp);
        fuelTemp += (power + qDecayEffective + qOxidation - toCoolant) / cf * dt;

        double net = toCoolant - removed;
        if (coolantTemp >= cfg.boilTemp - 0.5 && net > 0 && inventory > 0) {
            double latent = cfg.latentHeatSeconds / c;
            inventory = Math.max(0.0, inventory - net * dt / latent);
            coolantTemp = Math.max(coolantTemp, cfg.boilTemp);
        } else {
            coolantTemp += net / cc * dt;

            if (inventory < 1.0 && flow > 0.2 && heatSink > 0.2 && coolantTemp < cfg.boilTemp)
                inventory = Math.min(1.0, inventory + 0.01 * flow * dt);
        }
        coolantTemp = Math.max(coolantTemp, cfg.ambientTemp);
        fuelTemp = Math.max(fuelTemp, cfg.ambientTemp);
        coolantTemp -= (coolantTemp - cfg.ambientTemp) * 1e-5 * dt;
        fuelTemp = Math.min(fuelTemp, 3200.0);
    }

    private double hydrogenSaturation() {
        return clamp(hydrogen / 100.0, 0, 1);
    }

    private void damage(double dt) {
        if (fuelTemp > cfg.cladRuptureK)
            cladIntegrity = Math.max(0.0, cladIntegrity - (fuelTemp - cfg.cladRuptureK) / 40000.0 * dt);
        if (fuelTemp > cfg.oxidationK)
            hydrogen = Math.min(100.0, hydrogen + (fuelTemp - cfg.oxidationK) * 0.002 * dt);
        if (fuelTemp > cfg.controlMeltK)
            rodWorthMultiplier = Math.max(0.0, rodWorthMultiplier - (fuelTemp - cfg.controlMeltK) * 0.00005 * dt);
    }

    public DamageStage damageStage() {
        if (fuelTemp >= cfg.coriumK) return DamageStage.CORIUM;
        if (fuelTemp >= cfg.zircMeltK) return DamageStage.CORE_MELT;
        if (fuelTemp >= cfg.controlMeltK) return DamageStage.CORE_DEGRADATION;
        if (fuelTemp >= cfg.oxidationK) return DamageStage.OXIDATION;
        if (fuelTemp >= cfg.cladRuptureK) return DamageStage.CLAD_FAILURE;
        return DamageStage.NORMAL;
    }

    public double primaryPressureBar() {
        double x = clamp((coolantTemp - cfg.ambientTemp) / (cfg.ratedCoolantTemp - cfg.ambientTemp), 0, 3);
        return 1.0 + 154.0 * x * x;
    }

    public double thermalPower() {
        return power + decayHeatFraction();
    }

    public double electricFraction() {
        return clamp(power, 0, 1.2) * heatSink * (cladIntegrity > 0.2 ? 1.0 : 0.5);
    }

    public double stressUnits(double baseSU, double scale) {
        return baseSU * scale * electricFraction();
    }

    public double power() { return power; }
    public double fuelTemp() { return fuelTemp; }
    public double coolantTemp() { return coolantTemp; }
    public double xenon() { return xenon; }
    public double iodine() { return iodine; }
    public double samarium() { return samarium; }
    public double hydrogen() { return hydrogen; }
    public double cladIntegrity() { return cladIntegrity; }
    public double rodRegulating() { return rodRegulating; }
    public double rodShutdown() { return rodShutdown; }
    public double lastReactivity() { return lastReactivity; }
    public boolean isPrompt() { return prompt; }
    public boolean isScrammed() { return scram; }

    public double rodReserveDollars() {
        return rodCurve(rodRegulating) * cfg.regulatingRodWorth * rodWorthMultiplier;
    }

    public double inventory() { return inventory; }

    public void setInventory(double fraction) {
        this.inventory = clamp(fraction, 0, 1);
    }

    public void releaseHydrogen() {
        this.hydrogen = 0;
    }

    public double[] decayGroups() { return decay.clone(); }

    public static final int DATA_VERSION = 1;

    public double[] save() {
        double[] a = new double[17 + decay.length];
        int i = 0;
        a[i++] = DATA_VERSION;
        a[i++] = rodRegulating; a[i++] = rodShutdown; a[i++] = precursor; a[i++] = power;
        a[i++] = iodine; a[i++] = xenon; a[i++] = samarium; a[i++] = fuelTemp; a[i++] = coolantTemp;
        a[i++] = hydrogen; a[i++] = cladIntegrity; a[i++] = rodWorthMultiplier; a[i++] = rodTargetRegulating;
        a[i++] = scram ? 1 : 0; a[i++] = timeSinceScram; a[i++] = inventory;
        for (double d : decay)
            a[i++] = d;
        return a;
    }

    public void load(double[] a) {
        if (a == null || a.length < 17 + decay.length || (int) a[0] != DATA_VERSION)
            return;
        int i = 1;
        rodRegulating = a[i++]; rodShutdown = a[i++]; precursor = a[i++]; power = a[i++];
        iodine = a[i++]; xenon = a[i++]; samarium = a[i++]; fuelTemp = a[i++]; coolantTemp = a[i++];
        hydrogen = a[i++]; cladIntegrity = a[i++]; rodWorthMultiplier = a[i++]; rodTargetRegulating = a[i++];
        scram = a[i++] > 0.5; timeSinceScram = a[i++]; inventory = a[i++];
        for (int k = 0; k < decay.length; k++)
            decay[k] = a[i++];
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
