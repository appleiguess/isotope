package dev.isotopelib.sim.engine;

import java.util.ArrayList;
import java.util.List;

public final class PlantSim {
    public enum Mode { FULL, DRIVEN }

    public static final int DATA_VERSION = 3;

    public final PlantConfig cfg;
    public final CoreModel core;
    public final PrimaryLoop primary;
    public final SteamSystem steam;
    public final Protection rps;
    public final Electrical power;
    public final Containment containment;
    public final SourceTerm source;
    public final Rng rng;
    private Mode mode = Mode.FULL;

    private double drivenFlow = 1.0, drivenSink = 1.0, drivenLevel = 1.0, drivenPressure = 155.0;
    private double drivenInlet;
    private double drivenHot;
    private double drivenBase;
    public static final double AMBIENT_K = 293.15;

    private double time;
    private boolean vesselBreached;
    private double meltTime;
    private double lastPower = 1.0;
    private boolean anomaly;
    private boolean lastStep;
    private double boost = 1.0;
    private boolean lossOfAllCooling;
    private boolean protectionEnabled = true;

    public PlantSim(PlantConfig cfg) {
        this.cfg = cfg;
        this.rng = new Rng(cfg.seed);
        this.core = new CoreModel(cfg);
        this.primary = new PrimaryLoop(cfg);
        this.steam = new SteamSystem(cfg);
        this.rps = new Protection(cfg);
        this.power = new Electrical(cfg);
        this.containment = new Containment(cfg, rng);
        this.source = new SourceTerm(cfg);
        this.drivenInlet = cfg.inletTemp;
        this.drivenBase = cfg.inletTemp;
        this.drivenHot = cfg.ratedCoolantTemp + 13.0;
    }

    public void setProtectionEnabled(boolean on) {
        protectionEnabled = on;
    }

    public double boilOffRate() {
        if (mode != Mode.DRIVEN)
            return 0.0;
        double carried = Math.min(1.0, drivenFlow) * drivenSink * core.thermalPower();
        double excess = Math.max(0.0, core.heatToCoolant() - carried);
        return excess * 0.006 * Math.max(1.0, boost);
    }

    public void setDrivenTemperature(double k) {
        drivenBase = drivenInlet = drivenHot = k;
    }

    public void setMode(Mode m) {
        this.mode = m;
    }

    public Mode mode() {
        return mode;
    }

    public void setDriven(double flow, double heatSink, double levelFrac, double pressureBar) {
        drivenFlow = Math.max(0.0, flow);
        drivenSink = Math.max(0.0, Math.min(1.0, heatSink));
        drivenLevel = Math.max(0.0, Math.min(1.0, levelFrac));
        drivenPressure = pressureBar;
    }

    public void startAtPower(double p) {
        core.startAtPower(p, cfg.ratedPressure);
        primary.setPressure(cfg.ratedPressure);
        primary.setMass(1.0);
        double h = 0.5 * (cfg.ratedCoolantTemp + 13.0 + cfg.inletTemp);
        steam.setLevel(0.5);
        drivenHot = cfg.inletTemp + 25.0 * p;
        drivenInlet = cfg.inletTemp;
        drivenBase = cfg.inletTemp;
        lastPower = p;
    }

    public void step(double dt) {
        int n = (int) Math.ceil(dt / 0.05);
        double h = dt / n;
        for (int i = 0; i < n; i++)
            substep(h);
    }

    private void substep(double dt) {
        time += dt;
        power.step(dt);

        Protection.Readings r = new Protection.Readings();
        r.flux = core.power();
        r.thermalPower = core.thermalPower();
        r.dcAvailable = power.dcAvailable();
        r.stationBlackout = power.stationBlackout();
        if (mode == Mode.FULL) {
            r.pressureBar = primary.pressure();
            r.flow = primary.flow();
            r.sgLevel = steam.level();
            r.hotLegK = primary.tHot();
            r.containmentBar = containment.pressure();
            r.turbineTripped = steam.turbineTripped();
            r.mainFeedLost = !steam.mainFeedAvailable();
        } else {
            r.pressureBar = drivenPressure;
            r.flow = drivenFlow;
            r.sgLevel = 0.5;
            r.hotLegK = core.outletTemp();
            r.containmentBar = 1.0;
            r.heatUp = drivenBase < cfg.inletTemp - 10.0;
        }
        rps.evaluate(dt, r);
        core.setScram(rps.tripped() && (protectionEnabled || rps.manualActive()));

        double boostNow;
        if (mode == Mode.FULL) {
            boolean ac = power.acAvailable();
            boolean offsite = power.offsite();
            double tp = core.thermalPower();
            double uncooled = 1.0 - Math.min(1.0, steam.qRemoved() / Math.max(tp, 1e-6));
            boostNow = tp < 0.1 ? 1.0 + (cfg.timeCompression - 1.0) * uncooled : 1.0;
            primary.step(dt, core.outletTemp(), steam.qRemoved(), ac, offsite, rps.si(), containment.pressure(), rng, boostNow);
            core.step(dt, primary.flow(), primary.tCold(), primary.pressure(), primary.coreLevel(), boostNow);
            steam.step(dt, primary.tAvg(), primary.flow(), ac, power.dcAvailable(), rps.afw(), rps.tripped(), cfg.timeCompression);

            primary.addHydrogen(core.hydrogenStepKg());
            double h2 = primary.takeHydrogen(dt);
            if (vesselBreached)
                h2 += primary.takeHydrogen(dt) + primary.hydrogenPrimaryKg();
            containment.step(dt, primary.dischargeKgS(), h2, rps.spray(), ac);
            checkVessel(dt);
            source.step(dt, core.gapReleasedFraction(), core.meltFraction(), core.oxidationFraction(), primary.outflow(), primary.mass(),
                    containment.sprayOn(), containment.leakRate(), containment.ventRate(), steam.sgtrLeak(), steam.reliefFlow(), vesselBreached);
        } else {
            double tp = core.thermalPower();
            double uncooled = 1.0 - Math.min(1.0, drivenFlow * drivenSink);
            boostNow = tp < 0.1 ? 1.0 + (cfg.timeCompression - 1.0) * uncooled : 1.0;

            double baseTgt = tp > 0.005 ? cfg.inletTemp : AMBIENT_K;
            drivenBase += (baseTgt - drivenBase) * Math.min(1.0, dt / (baseTgt > drivenBase ? 90.0 : 900.0));
            double inletTarget = drivenBase + (1.0 - drivenSink) * Math.max(0.0, drivenHot - drivenBase);
            drivenInlet += (inletTarget - drivenInlet) * Math.min(1.0, dt / 8.0);
            core.step(dt, drivenFlow, drivenInlet, drivenPressure, drivenLevel, boostNow);
            drivenHot += (core.outletTemp() - drivenHot) * Math.min(1.0, dt / 2.0);
        }
        boost = boostNow;
        lastPower = core.power();
    }

    private void checkVessel(double dt) {
        if (vesselBreached)
            return;
        if (core.meltFraction() > 0.25)
            meltTime += dt;
        if (meltTime > 40.0 && core.meltFraction() > 0.4) {
            vesselBreached = true;
            primary.setMass(0.0);
            primary.setPressure(containment.pressure());
        }
    }

    public void setRodTargets(double regulating, double shutdown) {
        core.setRodTargets(regulating, shutdown);
    }

    public void manualTrip() {
        rps.manualTrip();
    }

    public void resetTrip() {
        rps.reset();
    }

    public void setBoronTarget(double ppm) {
        core.setBoronTarget(ppm);
    }

    public void setLoadDemand(double d) {
        steam.setLoadDemand(d);
    }

    public void setPumps(boolean on) {
        primary.setPumps(on);
    }

    public void openContainmentVent(boolean open) {
        containment.openVent(open);
    }

    public void inject(FailureEvent e, double parameter) {
        switch (e) {
            case PORV_STUCK_OPEN -> primary.stickPorvOpen();
            case LOCA -> primary.setBreakArea(parameter <= 0 ? 1.0 : parameter);
            case PUMP_TRIP -> primary.tripPump((int) parameter);
            case ALL_PUMPS_TRIP -> primary.setPumps(false);
            case LOSS_OF_OFFSITE_POWER -> power.setOffsite(false);
            case DIESELS_FLOODED -> power.floodDiesels();
            case STATION_BLACKOUT -> {
                power.setOffsite(false);
                power.floodDiesels();
            }
            case FEEDWATER_LOSS -> steam.tripTurbine();
            case TURBINE_TRIP -> steam.tripTurbine();
            case LOSS_OF_HEAT_SINK -> steam.setCondenserAvailable(false);
            case ROD_EJECTION -> core.ejectRod(parameter <= 0 ? 2.5 : parameter);
            case STUCK_RODS -> core.setStuckShutdownFraction(parameter <= 0 ? 0.2 : parameter);
            case SENSOR_DRIFT -> rps.setFault(Protection.Param.FLUX_HIGH, rng.nextInt(4), Protection.Fault.DRIFT, parameter);
            case SENSOR_DEAD -> rps.setFault(Protection.Param.PRESSURE_LOW, rng.nextInt(4), Protection.Fault.DEAD, 0);
            case STEAM_GENERATOR_TUBE_RUPTURE -> steam.setSgtrLeak(parameter <= 0 ? 0.01 : parameter);
            case CABLE_FIRE -> {
                int ch = rng.nextInt(4);
                for (Protection.Param p : Protection.Param.values())
                    rps.setFault(p, ch, Protection.Fault.DEAD, 0);
            }
            case BORON_DILUTION -> core.addBoron(-Math.abs(parameter));
        }
    }

    public double time() { return time; }
    public boolean vesselBreached() { return vesselBreached; }
    public double boost() { return boost; }

    public PlantSnapshot.DamageState damageState() {
        if (containment.breached() && vesselBreached)
            return PlantSnapshot.DamageState.RELEASE;
        if (vesselBreached)
            return PlantSnapshot.DamageState.VESSEL_BREACH;
        double melt = core.meltFraction();
        if (melt > 0.2)
            return PlantSnapshot.DamageState.CORE_MELT;
        if (melt > 0.0 || core.maxCladTemp() > 1700.0)
            return PlantSnapshot.DamageState.CORE_DEGRADATION;
        if (core.oxidationFraction() > 0.01 || core.maxCladTemp() > cfg.oxidationStartK + 300.0)
            return PlantSnapshot.DamageState.OXIDATION;
        if (core.ruptureFraction() > 0.0)
            return PlantSnapshot.DamageState.CLAD_FAILURE;
        if (rps.tripped() || primary.porvOpen() || core.minDnbr() < 1.3)
            return PlantSnapshot.DamageState.DEGRADED;
        if (rps.alarms() != 0)
            return PlantSnapshot.DamageState.OFF_NORMAL;
        return PlantSnapshot.DamageState.NORMAL;
    }

    public PlantSnapshot snapshot() {
        double period = periodSeconds();
        boolean full = mode == Mode.FULL;
        boolean safetyLost = !power.acAvailable() || !primary.hpi() && rps.si() && primary.pressure() < cfg.hpiShutoffBar;
        int ines = source.inesLevel(core.meltFraction(), core.claddingDamageFraction(), vesselBreached, containment.breached(), safetyLost,
                rps.alarms() != 0 || rps.faultyChannels() > 0);
        return new PlantSnapshot(
                core.power(), core.thermalPower(), core.reactivityDollars(), period, core.isPrompt(),
                core.rodRegulating(), core.rodShutdown(), core.rodReserveDollars(), core.xenonWorthDollars(), core.samariumWorthDollars(),
                core.boronPpm(), core.boronWorthDollars(), core.feedbackDollars(), core.fuelReactivityDollars(), core.axialOffset(),
                core.decayHeat(), core.meanBurnup(),
                full ? primary.tHot() : drivenHot, full ? primary.tCold() : drivenInlet, core.maxFuelTemp(), core.maxCladTemp(),
                core.meanFuelTemp(), core.meanVoidPercent(), core.minDnbr(),
                full ? primary.flow() : drivenFlow, full ? primary.pumpsRunning() : 4, full ? primary.pressure() : drivenPressure,
                full ? primary.mass() : drivenLevel, full ? primary.coreLevel() : drivenLevel, primary.porvOpen(), primary.porvStuck(),
                primary.hpi(), primary.lpi(), primary.accumulatorLeft(),
                steam.level(), steam.pressure(), steam.turbineFlow(), full ? steam.electricFraction() : 0.0, steam.turbineTripped(),
                steam.afwRunning(), steam.condensateStorage(),
                power.offsite(), power.acAvailable(), power.dcAvailable(), power.battery(), power.dieselFuel(), power.runningDiesels(),
                rps.tripped(), rps.tripMask(), rps.si(), rps.alarms(), rps.anyBypass(), rps.faultyChannels(),
                containment.pressure(), containment.integrity(), containment.hydrogenPercentVol(), core.hydrogenTotalKg(),
                core.claddingDamageFraction(), core.meltFraction(), core.oxidationFraction(), core.ruptureFraction(), vesselBreached,
                containment.breached(), damageState(),
                source.releasedPBq(), ines, source.environment());
    }

    public double periodSeconds() {
        double t = core.periodSeconds();
        return Double.isFinite(t) ? t : 1e9;
    }

    public double stablePeriodSeconds() {
        double rho = core.reactivityDollars();
        if (Math.abs(rho) < 1e-4 || core.isPrompt())
            return core.isPrompt() ? periodSeconds() : 1e9;
        double t = ReactorPhysics.periodSeconds(rho, cfg.beta, cfg.lambda, cfg.generationTime);
        return Double.isFinite(t) && t != 0.0 ? t : 1e9;
    }

    public double electricFraction() {
        if (mode == Mode.FULL)
            return steam.electricFraction();
        return Math.max(0.0, Math.min(1.2, core.power())) * drivenSink * (core.claddingDamageFraction() > 0.5 ? 0.5 : 1.0);
    }

    public double[] save() {
        List<Double> out = new ArrayList<>();
        out.add((double) DATA_VERSION);
        out.add(mode == Mode.FULL ? 0.0 : 1.0);
        out.add(time); out.add(vesselBreached ? 1.0 : 0.0); out.add(meltTime);
        out.add((double) (rng.state() & 0xFFFFFFFFL)); out.add((double) (rng.state() >>> 32));
        out.add(drivenInlet); out.add(drivenHot);
        core.save(out);
        primary.save(out);
        steam.save(out);
        rps.save(out);
        power.save(out);
        containment.save(out);
        source.save(out);
        double[] a = new double[out.size()];
        for (int i = 0; i < a.length; i++)
            a[i] = out.get(i);
        return a;
    }

    public boolean load(double[] a) {
        if (a == null || a.length == 0)
            return false;
        boolean old = (int) a[0] == 2;
        int expected = 9 + CoreModel.saveSize() + primary.saveSize() + steam.saveSize() + rps.saveSize() + power.saveSize()
                + containment.saveSize() + source.saveSize() - (old ? 1 : 0);
        if (a.length != expected || (int) a[0] != DATA_VERSION && !old)
            return false;
        int k = 1;
        mode = a[k++] > 0.5 ? Mode.DRIVEN : Mode.FULL;
        time = a[k++]; vesselBreached = a[k++] > 0.5; meltTime = a[k++];
        long lo = (long) a[k++], hi = (long) a[k++];
        rng.setState((hi << 32) | lo);
        drivenInlet = a[k++]; drivenHot = a[k++];
        drivenBase = drivenInlet;
        k = core.load(a, k, !old);
        k = primary.load(a, k);
        k = steam.load(a, k);
        k = rps.load(a, k);
        k = power.load(a, k);
        k = containment.load(a, k);
        k = source.load(a, k);
        return true;
    }
}
