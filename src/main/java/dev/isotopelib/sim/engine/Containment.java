package dev.isotopelib.sim.engine;

public final class Containment {
    private final PlantConfig cfg;
    private final Rng rng;

    private double steam;
    private double hydrogen;
    private double spike;
    private double integrity = 1.0;
    private boolean ventOpen;
    private boolean sprayOn;
    private boolean burned;
    private boolean detonated;
    private double leakRate;
    private double ventRate;
    private double pressure = 1.0;
    private double h2Percent;
    private double steamPerBar = 20.0;

    private static final double AIR_MOL = 2.0e6;
    private static final double MOL_PER_KG_H2 = 496.0;
    private static final double MOL_PER_TONNE_STEAM = 55500.0;

    public Containment(PlantConfig cfg, Rng rng) {
        this.cfg = cfg;
        this.rng = rng;
    }

    public void step(double dt, double dischargeTonnesPerSecond, double hydrogenKg, boolean spray, boolean ac) {
        sprayOn = spray && ac;
        steam += dischargeTonnesPerSecond * dt;
        hydrogen += hydrogenKg;

        double cool = cfg.containmentCoolingPerSecond * (ac ? 2.0 : 1.0) + (sprayOn ? cfg.sprayCoolingPerSecond : 0.0);
        steam -= steam * Math.min(1.0, cool * dt);

        ventRate = 0;
        if (ventOpen) {
            ventRate = clamp((pressure - 1.0) * 0.004, 0.0, 0.03);
            steam -= steam * ventRate * dt;
            hydrogen -= hydrogen * ventRate * dt;
        }

        double overDesign = Math.max(0.0, pressure - cfg.containmentDesignBar);
        leakRate = cfg.containmentLeakPerSecond + (1.0 - integrity) * 0.04 + overDesign * 3.0e-5;
        steam -= steam * leakRate * dt;
        hydrogen -= hydrogen * leakRate * dt * 0.5;

        double pct = hydrogenPercent();
        if (pct > cfg.recombinerStartPercent && ac)
            hydrogen = Math.max(0.0, hydrogen - cfg.recombinerKgPerSecond * Math.min(2.0, pct / 3.0) * dt);

        pct = hydrogenPercent();
        burned = false;
        detonated = false;
        if (pct >= cfg.hydrogenBurnPercent && rng.chance(Math.min(1.0, 0.03 * dt * (pct >= cfg.hydrogenDetonationPercent ? 20.0 : 1.0)))) {
            boolean det = pct >= cfg.hydrogenDetonationPercent;
            double frac = det ? 0.9 : 0.7;
            spike += Math.min(6.0, 0.5 * pct * frac);
            hydrogen *= (1.0 - frac);
            burned = true;
            detonated = det;
            if (det)
                integrity = Math.max(0.0, integrity - 0.35);
        }
        spike *= Math.exp(-dt / 20.0);

        pressure = 1.0 + steam / steamPerBar * cfg.containmentVolumeScale + spike;
        pressure = clamp(pressure, 1.0, 20.0);
        h2Percent = hydrogenPercent();

        if (pressure > cfg.containmentDesignBar * 1.5)
            integrity = Math.max(0.0, integrity - (pressure - cfg.containmentDesignBar * 1.5) * 0.004 * dt);
        if (pressure >= cfg.containmentFailureBar)
            integrity = 0.0;
    }

    private double hydrogenPercent() {
        double nH2 = hydrogen * MOL_PER_KG_H2;
        double nSteam = steam * MOL_PER_TONNE_STEAM;
        return 100.0 * nH2 / (AIR_MOL + nSteam + nH2);
    }

    public void openVent(boolean open) { ventOpen = open; }
    public void damage(double amt) { integrity = clamp(integrity - amt, 0.0, 1.0); }
    public void setIntegrity(double i) { integrity = clamp(i, 0.0, 1.0); }

    public double pressure() { return pressure; }
    public double steamTonnes() { return steam; }
    public double hydrogenKg() { return hydrogen; }
    public double hydrogenPercentVol() { return h2Percent; }
    public double integrity() { return integrity; }
    public boolean ventOpen() { return ventOpen; }
    public double leakRate() { return leakRate; }
    public double ventRate() { return ventRate; }
    public boolean burned() { return burned; }
    public boolean detonated() { return detonated; }
    public boolean breached() { return integrity <= 0.01; }
    public boolean sprayOn() { return sprayOn; }
    public double temperatureK() { return 300.0 + 60.0 * Math.min(2.0, pressure - 1.0); }

    public void save(java.util.List<Double> out) {
        out.add(steam); out.add(hydrogen); out.add(spike); out.add(integrity); out.add(ventOpen ? 1.0 : 0.0); out.add(pressure);
    }

    public int load(double[] a, int k) {
        steam = a[k++]; hydrogen = a[k++]; spike = a[k++]; integrity = a[k++]; ventOpen = a[k++] > 0.5; pressure = a[k++];
        return k;
    }

    public int saveSize() {
        return 6;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
