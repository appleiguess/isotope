package dev.isotopelib.sim.engine;

public final class SourceTerm {
    private final PlantConfig cfg;

    private double fpPrimary;
    private double fpContainment;
    private double fpEnvironment;
    private double fpSecondary;
    private double lastGap, lastMelt, lastOx;

    public SourceTerm(PlantConfig cfg) {
        this.cfg = cfg;
    }

    public void step(double dt, double gapFraction, double meltFraction, double oxidation, double primaryOutflow, double primaryMass,
                     boolean spray, double leakRate, double ventRate, double sgtrLeak, double secondaryRelief, boolean vesselBreached) {
        double dGap = Math.max(0.0, gapFraction - lastGap);
        double dMelt = Math.max(0.0, meltFraction - lastMelt);
        double dOx = Math.max(0.0, oxidation - lastOx);
        lastGap = Math.max(lastGap, gapFraction);
        lastMelt = Math.max(lastMelt, meltFraction);
        lastOx = Math.max(lastOx, oxidation);
        fpPrimary += cfg.gapReleaseFraction * dGap + 0.55 * dMelt + 0.05 * dOx;

        double toContainment = fpPrimary * Math.min(1.0, (primaryOutflow / Math.max(primaryMass, 0.05)) * dt * 4.0);
        if (vesselBreached)
            toContainment = fpPrimary * Math.min(1.0, dt * 0.2);
        fpPrimary -= toContainment;
        fpContainment += toContainment;

        double toSecondary = fpPrimary * Math.min(1.0, sgtrLeak * dt);
        fpPrimary -= toSecondary;
        fpSecondary += toSecondary;
        double outSecondary = fpSecondary * Math.min(1.0, secondaryRelief * 0.2 * dt);
        fpSecondary -= outSecondary;
        fpEnvironment += outSecondary;

        double removal = 0.0008 + (spray ? 0.012 : 0.0);
        double lost = fpContainment * Math.min(1.0, removal * dt);
        fpContainment -= lost;
        double leak = fpContainment * Math.min(1.0, leakRate * dt * 8.0);
        double vent = fpContainment * Math.min(1.0, ventRate * dt) / cfg.ventFilterFactor;
        double ventedUnfiltered = fpContainment * Math.min(1.0, ventRate * dt);
        fpContainment -= leak + ventedUnfiltered;
        fpEnvironment += leak + vent;
    }

    public double primary() { return fpPrimary; }
    public double containment() { return fpContainment; }
    public double secondary() { return fpSecondary; }
    public double environment() { return fpEnvironment; }

    public double releasedPBq() {
        return fpEnvironment * cfg.coreInventoryPBq;
    }

    public int inesLevel(double meltFraction, double claddingDamage, boolean vesselBreached, boolean containmentBreached,
                         boolean safetySystemsLost, boolean anomaly) {
        double pbq = releasedPBq();
        int byRelease = pbq >= 50 ? 7 : pbq >= 5 ? 6 : pbq >= 0.5 ? 5 : pbq >= 0.05 ? 4 : pbq >= 0.005 ? 3 : 0;
        int byState = 0;
        if (meltFraction > 0.3 || vesselBreached)
            byState = 5;
        else if (meltFraction > 0.01 || claddingDamage > 0.4)
            byState = 4;
        else if (claddingDamage > 0.05)
            byState = 3;
        else if (safetySystemsLost)
            byState = 2;
        else if (anomaly)
            byState = 1;
        int level = Math.max(byRelease, byState);
        if (containmentBreached && byState >= 5 && pbq > 0.5)
            level = Math.max(level, 6);
        return Math.min(7, level);
    }

    public void save(java.util.List<Double> out) {
        out.add(fpPrimary); out.add(fpContainment); out.add(fpEnvironment); out.add(fpSecondary);
        out.add(lastGap); out.add(lastMelt); out.add(lastOx);
    }

    public int load(double[] a, int k) {
        fpPrimary = a[k++]; fpContainment = a[k++]; fpEnvironment = a[k++]; fpSecondary = a[k++];
        lastGap = a[k++]; lastMelt = a[k++]; lastOx = a[k++];
        return k;
    }

    public int saveSize() {
        return 7;
    }
}
