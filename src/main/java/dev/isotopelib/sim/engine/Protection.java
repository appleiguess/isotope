package dev.isotopelib.sim.engine;

public final class Protection {
    public enum Param {
        FLUX_HIGH("Flux high"), PRESSURE_HIGH("Pressure high"), PRESSURE_LOW("Pressure low"), FLOW_LOW("Flow low"),
        SG_LEVEL_LOW("Steam generator level low-low"), OVER_TEMPERATURE("Over-temperature"), FLUX_RATE_HIGH("Flux rate high"),
        CONTAINMENT_HIGH("Containment pressure high"), TURBINE_TRIP("Turbine trip"), MANUAL("Manual trip"), NO_DC("No DC power");

        public final String text;

        Param(String text) {
            this.text = text;
        }
    }

    public enum Fault { OK, DRIFT, STUCK_LOW, STUCK_HIGH, DEAD }

    private static final int CH = 4;
    private final PlantConfig cfg;
    private final Fault[][] fault = new Fault[Param.values().length][CH];
    private final double[][] bias = new double[Param.values().length][CH];
    private final boolean[] bypass = new boolean[CH];
    private boolean tripped;
    private int tripMask;
    private boolean manual;
    private boolean siActive, afwActive, sprayActive;
    private double fluxPrev = 1.0, fluxRate;
    private int alarms;

    public Protection(PlantConfig cfg) {
        this.cfg = cfg;
        for (Fault[] row : fault)
            java.util.Arrays.fill(row, Fault.OK);
    }

    public static final class Readings {
        public double flux = 1.0, pressureBar = 155.0, flow = 1.0, sgLevel = 0.5, hotLegK = 573.0, containmentBar = 1.0;
        public boolean turbineTripped, dcAvailable = true, stationBlackout, mainFeedLost;
        public double thermalPower = 1.0;

        public boolean heatUp;
    }

    private double sensor(Param p, int ch, double truth, double nominal) {
        Fault f = fault[p.ordinal()][ch];
        return switch (f) {
            case OK -> truth;
            case DRIFT -> truth + bias[p.ordinal()][ch];
            case STUCK_LOW -> nominal;
            case STUCK_HIGH -> truth * 100.0 + 1000.0;
            case DEAD -> Double.NaN;
        };
    }

    private boolean demand(Param p, int ch, Readings r, double permissiveLowPower) {
        double high = 0;
        switch (p) {
            case FLUX_HIGH -> {
                double v = sensor(p, ch, r.flux, 1.0);
                double limit = r.thermalPower < 0.1 ? cfg.tripHighFluxLowPower : cfg.tripHighFlux;
                return Double.isNaN(v) || v > limit;
            }
            case PRESSURE_HIGH -> {
                double v = sensor(p, ch, r.pressureBar, 155.0);
                return Double.isNaN(v) || v > cfg.tripHighPressureBar;
            }
            case PRESSURE_LOW -> {
                if (permissiveLowPower > 0 || r.heatUp)
                    return false;
                double v = sensor(p, ch, r.pressureBar, 155.0);
                return Double.isNaN(v) || v < cfg.tripLowPressureBar;
            }
            case FLOW_LOW -> {
                if (permissiveLowPower > 0)
                    return false;
                double v = sensor(p, ch, r.flow, 1.0);
                return Double.isNaN(v) || v < cfg.tripLowFlow;
            }
            case SG_LEVEL_LOW -> {
                double v = sensor(p, ch, r.sgLevel, 0.5);
                return Double.isNaN(v) || v < cfg.tripLowLowSgLevel;
            }
            case OVER_TEMPERATURE -> {
                double v = sensor(p, ch, r.hotLegK, 573.0);
                return Double.isNaN(v) || v > cfg.tripOverTemperatureK;
            }
            case FLUX_RATE_HIGH -> {
                double v = sensor(p, ch, fluxRate, 0.0);
                return Double.isNaN(v) || (v > cfg.tripHighPositiveRate && r.thermalPower > 0.1);
            }
            case CONTAINMENT_HIGH -> {
                double v = sensor(p, ch, r.containmentBar, 1.0);
                return Double.isNaN(v) || v > cfg.tripHighContainmentBar;
            }
            case TURBINE_TRIP -> {
                return r.turbineTripped && r.thermalPower > 0.5 && fault[p.ordinal()][ch] != Fault.STUCK_LOW;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean vote(Param p, Readings r, double permissive) {
        int demanding = 0, active = 0;
        for (int ch = 0; ch < CH; ch++) {
            if (bypass[ch])
                continue;
            active++;
            if (demand(p, ch, r, permissive))
                demanding++;
        }
        return demanding >= 2 || (active <= 2 && demanding >= 1);
    }

    public void evaluate(double dt, Readings r) {
        fluxRate = (r.flux - fluxPrev) / Math.max(dt, 1e-6);
        fluxPrev = r.flux;
        double permissive = r.thermalPower < 0.1 ? 1.0 : 0.0;
        int mask = 0;
        for (Param p : Param.values()) {
            boolean t = switch (p) {
                case MANUAL -> manual;
                case NO_DC -> !r.dcAvailable;
                default -> vote(p, r, permissive);
            };
            if (t)
                mask |= 1 << p.ordinal();
        }
        tripMask = mask;
        if (mask != 0)
            tripped = true;

        boolean lowP = vote(Param.PRESSURE_LOW, r, 0.0);
        boolean highC = vote(Param.CONTAINMENT_HIGH, r, 0.0);
        siActive = siActive || lowP || highC;
        boolean sgLow = vote(Param.SG_LEVEL_LOW, r, 0.0);
        afwActive = sgLow || r.stationBlackout || r.mainFeedLost || tripped;
        boolean highHigh = r.containmentBar > cfg.sprayHighBar;
        sprayActive = highHigh;

        int al = 0;
        if (r.flux > 1.03) al |= 1;
        if (r.pressureBar > 160 || r.pressureBar < 145) al |= 2;
        if (r.flow < 0.95) al |= 4;
        if (r.sgLevel < 0.3 || r.sgLevel > 0.7) al |= 8;
        if (r.hotLegK > 585) al |= 16;
        if (r.containmentBar > 1.1) al |= 32;
        alarms = al;
    }

    public void manualTrip() {
        manual = true;
    }

    public void reset() {
        manual = false;
        tripped = false;
        siActive = false;
    }

    public void setBypass(int channel, boolean on) {
        if (channel >= 0 && channel < CH)
            bypass[channel] = on;
    }

    public void setFault(Param p, int channel, Fault f, double biasValue) {
        fault[p.ordinal()][channel] = f;
        bias[p.ordinal()][channel] = biasValue;
    }

    public void repairAll() {
        for (Fault[] row : fault)
            java.util.Arrays.fill(row, Fault.OK);
    }

    public boolean tripped() { return tripped; }
    public boolean manualActive() { return manual; }
    public int tripMask() { return tripMask; }
    public boolean si() { return siActive; }
    public boolean afw() { return afwActive; }
    public boolean spray() { return sprayActive; }
    public int alarms() { return alarms; }
    public boolean anyBypass() {
        for (boolean b : bypass)
            if (b)
                return true;
        return false;
    }
    public int faultyChannels() {
        int n = 0;
        for (Fault[] row : fault)
            for (Fault f : row)
                if (f != Fault.OK)
                    n++;
        return n;
    }

    public void save(java.util.List<Double> out) {
        out.add(tripped ? 1.0 : 0.0);
        out.add(manual ? 1.0 : 0.0);
        out.add(siActive ? 1.0 : 0.0);
        out.add(fluxPrev);
        for (boolean b : bypass)
            out.add(b ? 1.0 : 0.0);
        for (int p = 0; p < fault.length; p++)
            for (int c = 0; c < CH; c++) {
                out.add((double) fault[p][c].ordinal());
                out.add(bias[p][c]);
            }
    }

    public int load(double[] a, int k) {
        tripped = a[k++] > 0.5;
        manual = a[k++] > 0.5;
        siActive = a[k++] > 0.5;
        fluxPrev = a[k++];
        for (int c = 0; c < CH; c++)
            bypass[c] = a[k++] > 0.5;
        for (int p = 0; p < fault.length; p++)
            for (int c = 0; c < CH; c++) {
                fault[p][c] = Fault.values()[(int) a[k++]];
                bias[p][c] = a[k++];
            }
        return k;
    }

    public int saveSize() {
        return 4 + CH + Param.values().length * CH * 2;
    }
}
