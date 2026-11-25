package dev.isotopelib.sim.chain;

public final class ChainMath {
    private ChainMath() {
    }

    public static double temperatureFactor(double coolantC, double referenceC) {
        double span = Math.max(1.0, referenceC - 100.0);
        return clamp((coolantC - 100.0) / span, 0.0, 1.25);
    }

    public static double exchangerOutput(double coolantC, double hotMb, double roomMb, double maxPerSec,
                                         double efficiency, double outPerHot, double referenceC, double dt) {
        double byHeat = hotMb * outPerHot * efficiency * temperatureFactor(coolantC, referenceC);
        double byRating = maxPerSec * dt;
        return Math.max(0.0, Math.min(Math.min(byHeat, byRating), roomMb));
    }

    public static double simplePowerTarget(int insertion, double enrichment, int fuelMb, int chs, double layoutDollars) {
        double rods = clamp(1.0 - insertion / 100.0, 0.0, 1.0);
        double fuel = clamp(enrichment / 4.0, 0.0, 1.15) * Math.sqrt(clamp(fuelMb / (Math.max(1, chs) * 150.0), 0.0, 1.0));
        double layout = clamp(1.0 + 0.15 * layoutDollars, 0.4, 1.1);
        return clamp(rods * fuel * layout, 0.0, 1.2);
    }

    public static double simpleTemperatureStep(double t, double heat, double cool, double ambient, double heatRate, double coolRate, double dt) {
        return Math.max(ambient, t + (heat * heatRate - cool * coolRate * (t - ambient)) * dt);
    }

    public static double heatSink(double steamPerSec, double steamAtFull, double thermalPower) {
        if (thermalPower < 0.02)
            return 1.0;
        double removed = steamPerSec / Math.max(1e-6, steamAtFull);
        return clamp(removed / thermalPower, 0.0, 1.0);
    }

    public static boolean turbineLevelChange(int cur, int wanted, int levels, long ticksSinceLast) {
        if (wanted == cur)
            return false;
        if (ticksSinceLast < 100)
            return false;
        return (wanted == 0) != (cur == 0) || Math.abs(wanted - cur) >= 2 || wanted == levels;
    }

    public static double turbineLag(double cur, double tgt, double inertiaSeconds, double dt) {
        if (inertiaSeconds <= 1e-6)
            return tgt;
        return cur + (tgt - cur) * (1.0 - Math.exp(-dt / inertiaSeconds));
    }

    public static double turbineCoast(double current, double inertiaSeconds, double dt) {
        return turbineLag(current, 0.0, inertiaSeconds * 1.5, dt);
    }

    public static double stressUnits(double baseSu, double turbinesPerPlant, double loadFraction) {
        return baseSu / Math.max(1.0, turbinesPerPlant) * clamp(loadFraction, 0.0, 1.2);
    }

    public static double capacityPerRpm(double baseSu, double turbinesPerPlant, double rpm) {
        return baseSu / Math.max(1.0, turbinesPerPlant) / Math.max(1.0, rpm);
    }

    public static int unitsForFullPower(double steamAtFull, double maxSteamPerUnit) {
        return (int) Math.ceil(steamAtFull / Math.max(1e-6, maxSteamPerUnit) - 1e-9);
    }

    public static double ventPush(double storedMb, double maxPerSec, double dt, boolean pathFree) {
        if (!pathFree)
            return 0.0;
        return Math.max(0.0, Math.min(storedMb, maxPerSec * dt));
    }

    public static double backPressureBar(double steamFill, double barAtFull) {
        return barAtFull * clamp((steamFill - 0.8) / 0.2, 0.0, 1.0);
    }

    public static double steamPressureBar(double steamFill, double barAtFull) {
        return 1.0 + (barAtFull - 1.0) * clamp(steamFill, 0.0, 1.0);
    }

    public static double condenseRate(int volumeBlocks, int ice, double basePerBlock, double perIce) {
        if (volumeBlocks <= 0)
            return 0.0;
        return Math.max(0.0, basePerBlock * volumeBlocks + perIce * Math.max(0, ice));
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
