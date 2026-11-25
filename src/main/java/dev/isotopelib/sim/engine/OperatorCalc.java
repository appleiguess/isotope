package dev.isotopelib.sim.engine;

public final class OperatorCalc {
    private OperatorCalc() {
    }

    public static double interpolate(float[] table, int off, int points, double x) {
        if (points <= 0)
            return 0.0;
        if (points == 1)
            return table[off];
        double t = Math.max(0.0, Math.min(1.0, x)) * (points - 1);
        int i = Math.min(points - 2, (int) Math.floor(t));
        double f = t - i;
        return table[off + i] * (1.0 - f) + table[off + i + 1] * f;
    }

    public static double reactivityAtRods(float[] table, int offset, int points, double insertion) {
        return interpolate(table, offset, points, insertion);
    }

    public static double kEff(double rhoDollars, double betaEff) {
        return ReactorPhysics.kEff(rhoDollars, betaEff);
    }

    public static double period(double rhoDollars, double[] beta, double[] lambda, double genTime) {
        if (rhoDollars >= 1.0)
            return 0.0;
        return ReactorPhysics.periodSeconds(rhoDollars, beta, lambda, genTime);
    }

    public static double maxReactivityForPeriod(double periodSeconds, double[] beta, double[] lambda, double genTime) {
        if (!(periodSeconds > 0))
            return 0.0;
        return ReactorPhysics.inhourRho(1.0 / periodSeconds, beta, lambda, genTime);
    }

    public static double xenonAfter(float[] curve, int off, int points, double hours, double curveHours) {
        return interpolate(curve, off, points, hours / curveHours);
    }

    public static double restartMargin(double rhoRodsOutNow, double xenonNow, double xenonThen) {
        return rhoRodsOutNow + (xenonThen - xenonNow);
    }

    public static double decayHeat(double hours, double operatingSeconds) {
        return ReactorPhysics.wayWigner(hours * 3600.0, operatingSeconds);
    }

    public static double steamAt(double powerFraction, double steamAtFull, double steamPerHeat) {
        return Math.max(0.0, powerFraction) * steamAtFull * steamPerHeat;
    }

    public static int turbinesFor(double steamPerSecond, double turbineMaxSteam) {
        if (steamPerSecond <= 0 || turbineMaxSteam <= 0)
            return 0;
        return (int) Math.ceil(steamPerSecond / turbineMaxSteam - 1e-9);
    }
}
