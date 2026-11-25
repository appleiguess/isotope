package dev.isotopelib.sim.engine;

public record PhysicsReadout(
        double rhoDollars, double rhoPcm, double betaEff, double genTime, double period, double startupRate, double kEff,
        double multiplication, boolean prompt, double sdm, double ecp, double alphaDoppler, double alphaModerator, double alphaVoid,
        double boronPcmPerPpm, double[] groups,
        double thermalMW, double fissionRate, double moderatorK, double sigmaFBarn, double n235, double sigmaF, double flux,
        double neutronDensity,
        double massFlow, double cp, double qCalorimetric, double satTempK, double subcooling, double qLinAvg, double qLinMax,
        double peaking, double pelletRise, double carnot, double efficiency, double electricMW,
        double decayHeat, double wayWigner, double sinceShutdown, double operatingSeconds,
        double xenon, double iodine, double xenonEquilibrium, double xenonPeakHours, double xenonPeakWorth, double[] xenonCurve,
        double deadFromHours, double deadToHours, double restartMargin, double timeCompression) {
}
