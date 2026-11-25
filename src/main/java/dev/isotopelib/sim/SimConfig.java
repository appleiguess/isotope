package dev.isotopelib.sim;

public final class SimConfig {
    public double timeCompression = 120.0;

    public double lambdaDelayed = 0.08;

    public double promptTau = 0.1;

    public double maxPromptPower = 30.0;

    public double sourceLevel = 1e-7;

    public double fuelExcessDollars = 14.0;

    public double shimDollars = 6.0;
    public double regulatingRodWorth = 12.0;
    public double shutdownRodWorth = 10.0;
    public double dopplerPerKelvin = -0.006;
    public double coolantTempPerKelvin = -0.08;

    public double voidPerPercent = -0.05;

    public double refFuelTemp = 900.0;

    public double minFeedbackFuelTemp = 600.0;
    public double refCoolantTemp = 573.0;
    public double xenonEquilibriumWorth = 3.0;
    public double samariumEquilibriumWorth = 0.8;

    public double positiveScramDollars = 0.0;

    public double rodSpeed = 0.01;

    public double scramTime = 2.0;

    public double ratedFuelTemp = 900.0;
    public double ratedCoolantTemp = 573.0;
    public double inletTemp = 548.0;
    public double ambientTemp = 300.0;

    public double fuelTimeConstant = 8.0;
    public double coolantTimeConstant = 10.0;

    public double naturalCirculation = 0.04;

    public double boilTemp = 590.0;
    public double boilSpan = 80.0;

    public double latentHeatSeconds = 188.0;

    public double[] decayTaus = {60.0, 1500.0, 40000.0, 2000000.0};
    public double[] decayAmplitudes = {0.03752991661159219, 0.012553027741692958, 0.012210855410622348, 0.0027062002360925524};

    public double iodineHalfLifeHours = 6.57;
    public double xenonHalfLifeHours = 9.2;

    public double xenonBurnRatio = 3.2;

    public double xenonDirectShare = 0.038;

    public double samariumTauHours = 150.0;

    public double cladRuptureK = 1100.0;
    public double oxidationK = 1500.0;
    public double controlMeltK = 1700.0;
    public double zircMeltK = 2150.0;
    public double coriumK = 2200.0;

    public static SimConfig lightWater() {
        return new SimConfig();
    }

    public static SimConfig graphite() {
        SimConfig c = new SimConfig();
        c.voidPerPercent = 0.04;
        c.coolantTempPerKelvin = -0.004;
        c.positiveScramDollars = 1.4;
        c.scramTime = 18.0;
        return c;
    }
}
