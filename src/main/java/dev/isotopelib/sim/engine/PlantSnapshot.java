package dev.isotopelib.sim.engine;

public record PlantSnapshot(

        double power, double thermalPower, double reactivityDollars, double periodSeconds, boolean prompt,
        double rodRegulating, double rodShutdown, double rodReserveDollars, double xenonWorth, double samariumWorth, double boronPpm,
        double boronWorth, double feedbackDollars, double fuelDollars, double axialOffset, double decayHeat, double burnupGWd,

        double hotLegK, double coldLegK, double maxFuelK, double maxCladK, double meanFuelK, double voidPercent, double minDnbr,
        double flow, double pumpsRunning, double pressureBar, double primaryMass, double coreLevel, boolean porvOpen, boolean porvStuck,
        boolean hpi, boolean lpi, double accumulatorLeft,

        double sgLevel, double secondaryBar, double steamFlow, double electricFraction, boolean turbineTripped, boolean afwRunning,
        double condensateStorage,

        boolean offsite, boolean acAvailable, boolean dcAvailable, double battery, double dieselFuel, int dieselsRunning,

        boolean tripped, int tripMask, boolean safetyInjection, int alarms, boolean bypassActive, int faultyChannels,

        double containmentBar, double containmentIntegrity, double hydrogenPercent, double hydrogenKgTotal,
        double claddingDamage, double meltFraction, double oxidation, double ruptureFraction, boolean vesselBreached,
        boolean containmentBreached, DamageState damage,

        double releasedPBq, int inesLevel, double environmentFraction) {
    public enum DamageState {
        NORMAL, OFF_NORMAL, DEGRADED, CLAD_FAILURE, OXIDATION, CORE_DEGRADATION, CORE_MELT, VESSEL_BREACH, CONTAINMENT_CHALLENGE, RELEASE
    }
}
