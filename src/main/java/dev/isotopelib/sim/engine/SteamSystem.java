package dev.isotopelib.sim.engine;

public final class SteamSystem {
    private final PlantConfig cfg;

    private double level = 0.5;
    private double pressure;
    private double steamProduced;
    private double turbineFlow;
    private double dumpFlow;
    private double reliefFlow;
    private double qRemoved;
    private boolean turbineTripped;
    private boolean condenserAvailable = true;
    private boolean mainFeedAvailable = true;
    private double loadDemand = 1.0;
    private double condensateStorage = 1.0;
    private double sgtrLeak;
    private boolean msivClosed;
    private double electric;
    private boolean afwRunning;
    private double ua;

    public SteamSystem(PlantConfig cfg) {
        this.cfg = cfg;
        this.pressure = cfg.secondaryRatedBar;
        double tAvgRated = cfg.ratedCoolantTemp + 0.5 * 25.0 * 0.0 + 0.5;
        this.ua = cfg.steamGeneratorUA / Math.max(1.0, (0.5 * (cfg.ratedCoolantTemp + 13.0 + cfg.inletTemp)) - Steam.satTemp(cfg.secondaryRatedBar));
    }

    public void step(double dt, double tAvgPrimary, double flowPrimary, boolean ac, boolean dc, boolean afwSignal, boolean tripSignal) {
        step(dt, tAvgPrimary, flowPrimary, ac, dc, afwSignal, tripSignal, cfg.timeCompression);
    }

    public void step(double dt, double tAvgPrimary, double flowPrimary, boolean ac, boolean dc, boolean afwSignal, boolean tripSignal, double compression) {
        double tSat = Steam.satTemp(pressure);
        double levelFactor = clamp(level / 0.3, 0.0, 1.0);
        double flowFactor = clamp(flowPrimary / 0.3, 0.05, 1.0);
        qRemoved = Math.max(0.0, ua * (tAvgPrimary - tSat) * levelFactor * flowFactor);
        steamProduced = qRemoved;

        if (tripSignal)
            turbineTripped = true;
        if (!condenserAvailable && !ac)
            turbineTripped = true;
        if (loadDemand <= 0.0 && !turbineTripped)
            turbineFlow = 0.0;
        double pr = Math.sqrt(Math.max(0.0, pressure) / cfg.secondaryRatedBar);
        double demand = turbineTripped || msivClosed ? 0.0 : clamp(loadDemand, 0.0, 1.1);
        turbineFlow = demand * pr;

        double dumpOpen = condenserAvailable && !msivClosed ? clamp((pressure - (cfg.secondaryRatedBar + 3.0)) / 6.0, 0.0, 1.0) : 0.0;
        dumpFlow = dumpOpen * cfg.steamDumpFraction * pr;

        double reliefOpen = clamp((pressure - cfg.secondaryReliefBar) / 6.0, 0.0, 1.0);
        reliefFlow = reliefOpen * 0.5 * pr;

        double out = turbineFlow + dumpFlow + reliefFlow;
        pressure += 2.5 * (steamProduced - out) * dt;
        pressure = clamp(pressure, 1.0, 120.0);

        if (steamProduced > 0 && pressure < Steam.satPressure(tAvgPrimary - 5.0) && out < steamProduced)
            pressure += 0.2 * dt;

        double feed = 0.0;
        mainFeedAvailable = ac && condenserAvailable && !turbineTripped;
        if (mainFeedAvailable)
            feed = clamp(steamProduced + 2.0 * (0.5 - level), 0.0, 1.3);
        afwRunning = false;
        if (ac)
            condensateStorage = Math.min(1.0, condensateStorage + dt * 0.02);
        if (afwSignal && condensateStorage > 0 && (dc || ac) && pressure > 3.0) {
            double afw = cfg.auxFeedwaterFraction * clamp(0.6 + (0.4 - level), 0.0, 1.5);
            if (level < 0.55) {
                feed += afw;
                afwRunning = true;
                condensateStorage = Math.max(0.0, condensateStorage - dt * cfg.timeCompression / (3.0 * 3600.0));
            }
        }

        double slow = 1.0 + (compression - 1.0) * clamp(1.0 - steamProduced / 0.1, 0.0, 1.0);
        level += (feed * slow - steamProduced * slow) * dt / 120.0;
        level = clamp(level, 0.0, 1.0);

        electric = turbineTripped ? 0.0 : clamp(turbineFlow, 0.0, 1.1);
    }

    public void setLoadDemand(double d) { loadDemand = clamp(d, 0.0, 1.1); }
    public void resetTurbine() { if (condenserAvailable) turbineTripped = false; }
    public void tripTurbine() { turbineTripped = true; }
    public void setCondenserAvailable(boolean on) { condenserAvailable = on; }
    public void closeMsiv(boolean closed) { msivClosed = closed; }
    public void setSgtrLeak(double leak) { sgtrLeak = Math.max(0.0, leak); }
    public void refillCondensate() { condensateStorage = 1.0; }
    public void setLevel(double l) { level = clamp(l, 0.0, 1.0); }

    public double level() { return level; }
    public double pressure() { return pressure; }
    public double qRemoved() { return qRemoved; }
    public double steamProduced() { return steamProduced; }
    public double turbineFlow() { return turbineFlow; }
    public double dumpFlow() { return dumpFlow; }
    public double reliefFlow() { return reliefFlow; }
    public boolean turbineTripped() { return turbineTripped; }
    public boolean condenserAvailable() { return condenserAvailable; }
    public boolean mainFeedAvailable() { return mainFeedAvailable; }
    public boolean afwRunning() { return afwRunning; }
    public double condensateStorage() { return condensateStorage; }
    public double sgtrLeak() { return sgtrLeak; }
    public double electricFraction() { return electric; }
    public double loadDemand() { return loadDemand; }

    public double sinkQuality() { return clamp(Math.max(qRemoved, afwRunning ? cfg.auxFeedwaterFraction : 0.0) / 1.0 * 4.0 + (level > 0.25 ? 0.2 : 0.0), 0.0, 1.0); }

    public void save(java.util.List<Double> out) {
        out.add(level); out.add(pressure); out.add(condensateStorage); out.add(turbineTripped ? 1.0 : 0.0);
        out.add(condenserAvailable ? 1.0 : 0.0); out.add(loadDemand); out.add(sgtrLeak); out.add(msivClosed ? 1.0 : 0.0);
        out.add(qRemoved); out.add(steamProduced); out.add(turbineFlow); out.add(electric);
    }

    public int load(double[] a, int k) {
        level = a[k++]; pressure = a[k++]; condensateStorage = a[k++]; turbineTripped = a[k++] > 0.5;
        condenserAvailable = a[k++] > 0.5; loadDemand = a[k++]; sgtrLeak = a[k++]; msivClosed = a[k++] > 0.5;
        qRemoved = a[k++]; steamProduced = a[k++]; turbineFlow = a[k++]; electric = a[k++];
        return k;
    }

    public int saveSize() {
        return 12;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
