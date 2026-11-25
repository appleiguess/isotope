package dev.isotopelib.sim.engine;

public final class PlantConfig {
    public static final int N = 8;

    public double timeCompression = 60.0;
    public double ratedThermalMW = 3000.0;
    public long seed = 20260101L;

    public double[] beta = {0.000215, 0.001424, 0.001274, 0.002568, 0.000748, 0.000273};
    public double[] lambda = {0.0124, 0.0305, 0.111, 0.301, 1.14, 3.01};

    public double maxPromptPower = 30.0;
    public double sourceLevel = 1e-7;

    public double generationTime = 2.0e-5;

    public double coreVolumeM3 = 32.6;
    public int fuelRods = 50952;
    public double activeHeightM = 3.66;

    public double radialPeaking = 1.40;

    public double chfFactor = 1.0;

    public double fuelConductivity = 3.0;

    public double uo2Density = 10.4;
    public double fuelVolumeFraction = 0.30;

    public double u235DepletionBurnup = 28.0;

    public double ratedLoopDeltaT = 25.0;

    public double condenserK = 313.15;

    public double fuelExcessBol = 17.0;

    public double excessLossPerGWd = 0.2;
    public double burnableAbsorberWorth = 3.0;

    public double burnableAbsorberBurnup = 8.0;
    public double maxBurnup = 45.0;

    public double assemblyLifeSeconds = 10800.0;
    public double nominalEnrichment = 4.0;
    public double regulatingRodWorth = 12.0;
    public double shutdownRodWorth = 10.0;
    public double rodSpeed = 0.015;
    public double scramTime = 2.0;

    public double positiveScramDollars = 0.0;
    public double boronWorthPerPpm = -0.0155;
    public double boronStartPpm = 400.0;
    public double boronMaxPpm = 2400.0;

    public double boronRatePpmPerSecond = 0.6;

    public double kDoppler = -0.33;
    public double kModerator = -0.115;

    public double kVoid = -0.05;
    public double refFuelTemp = 900.0;
    public double refCoolantTemp = 560.0;
    public double minFeedbackFuelTemp = 600.0;
    public double minFeedbackCoolantTemp = 548.0;

    public double shapeKappa = 0.04;
    public double tauShape = 30.0;

    public double tauNaturalShape = 600.0;
    public double tauDecayShape = 120.0;

    public double iodineHalfLifeHours = 6.57;
    public double xenonHalfLifeHours = 9.2;
    public double xenonBurnRatio = 3.2;
    public double xenonDirectShare = 0.038;
    public double xenonEquilibriumWorth = 4.2;
    public double samariumTauHours = 225.0;
    public double promethiumHalfLifeHours = 53.1;
    public double samariumEquilibriumWorth = 0.8;

    public double[] decayTaus = {60.0, 1500.0, 40000.0, 2000000.0};
    public double[] decayAmplitudes = {0.03752991661159219, 0.012553027741692958, 0.012210855410622348, 0.0027062002360925524};

    public double ratedFuelTemp = 900.0;
    public double ratedCladTemp = 620.0;
    public double ratedCoolantTemp = 560.0;
    public double inletTemp = 548.0;
    public double ambientTemp = 300.0;
    public double fuelTimeConstant = 8.0;
    public double cladTimeConstant = 1.0;
    public double thermalTau = 0.0;
    public double voidTau = 0.0;
    public double dopplerScale = 1.0;

    public double coreTransitTime = 3.5;

    public double latentKelvin = 150.0;
    public double subcooledBoilingKelvin = 20.0;

    public double ratedDnbr = 2.6;
    public double filmBoilingFactor = 0.12;
    public double steamCoolingFactor = 0.06;

    public double cladRuptureK = 1100.0;
    public double oxidationStartK = 1100.0;
    public double zircMeltK = 2150.0;
    public double uo2MeltK = 3120.0;

    public double oxidationEnergySeconds = 43.0;

    public double zirconiumHydrogenKg = 440.0;

    public double oxidationRate = 1250.0;
    public double oxidationActivationK = 22900.0;

    public int pumps = 4;
    public double pumpCoastdownSeconds = 25.0;
    public double naturalCirculation = 0.07;
    public double ratedPressure = 155.0;
    public double heaterOnBar = 153.0;
    public double sprayOnBar = 158.0;
    public double poRvOpenBar = 162.0;
    public double poRvCloseBar = 155.5;
    public double safetyValveBar = 172.0;
    public double heaterBarPerSecond = 0.25;
    public double sprayBarPerSecond = 0.7;
    public double swellBarPerKelvin = 1.2;
    public double inventoryPressureBar = 500.0;
    public double flashBarPerKelvinSecond = 0.6;

    public double poRvMassFraction = 6.0e-4;
    public double breakMassFraction = 1.2e-2;
    public double poRvStuckChance = 0.0;

    public double coreTopMass = 0.62;
    public double coreBottomMass = 0.25;

    public double hpiShutoffBar = 120.0;
    public double hpiMassFraction = 3.0e-3;
    public double lpiOnBar = 20.0;
    public double lpiMassFraction = 6.0e-3;
    public double accumulatorOnBar = 40.0;
    public double accumulatorVolume = 0.30;
    public double accumulatorMassFraction = 0.02;
    public double rwstBoronPpm = 2400.0;

    public double steamGeneratorUA = 1.0;
    public double secondaryRatedBar = 62.0;
    public double secondaryReliefBar = 85.0;
    public double steamDumpFraction = 0.40;
    public double steamGeneratorInventory = 1.0;
    public double auxFeedwaterFraction = 0.06;
    public double turbineEfficiency = 0.34;
    public double condenserCapacity = 1.0;

    public double dieselStartSeconds = 10.0;
    public double dieselFuelHours = 24.0;
    public double batteryHours = 8.0;
    public int diesels = 2;

    public double tripHighFlux = 1.09;
    public double tripHighFluxLowPower = 0.25;
    public double tripHighPositiveRate = 0.5;
    public double tripHighPressureBar = 166.0;
    public double tripLowPressureBar = 132.0;
    public double tripLowFlow = 0.87;
    public double tripLowLowSgLevel = 0.17;
    public double tripOverTemperatureK = 618.0;
    public double tripHighContainmentBar = 1.3;
    public double siLowPressureBar = 122.0;
    public double sprayHighBar = 2.4;
    public double sensorFailurePerHour = 0.0;

    public double containmentDesignBar = 4.0;
    public double containmentFailureBar = 8.0;
    public double containmentVolumeScale = 1.0;
    public double containmentLeakPerSecond = 2.0e-6;
    public double containmentCoolingPerSecond = 0.004;
    public double sprayCoolingPerSecond = 0.03;
    public double recombinerKgPerSecond = 0.02;
    public double recombinerStartPercent = 1.5;
    public double hydrogenBurnPercent = 4.0;
    public double hydrogenDetonationPercent = 10.0;
    public double ventFilterFactor = 1000.0;

    public double coreInventoryPBq = 3200.0;
    public double gapReleaseFraction = 0.03;

    public static PlantConfig standard() {
        return new PlantConfig();
    }

    public static PlantConfig forgiving() {
        PlantConfig c = new PlantConfig();
        c.tripHighFlux = 1.06;
        c.breakMassFraction = 2.0e-3;
        c.poRvStuckChance = 0.0;
        c.oxidationRate = 1.0e8;
        c.dieselFuelHours = 72.0;
        c.batteryHours = 24.0;
        c.timeCompression = 240.0;
        return c;
    }

    public static PlantConfig hardcore() {
        PlantConfig c = new PlantConfig();
        c.poRvStuckChance = 0.15;
        c.sensorFailurePerHour = 2.0;
        c.dieselFuelHours = 8.0;
        c.batteryHours = 4.0;
        c.timeCompression = 120.0;
        return c;
    }

    public static PlantConfig graphite() {
        PlantConfig c = new PlantConfig();
        c.kVoid = 0.045;
        c.kModerator = -0.01;
        c.positiveScramDollars = 1.4;
        c.scramTime = 18.0;
        return c;
    }
}
