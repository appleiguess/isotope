package dev.isotopelib.sim.engine;

public final class Steam {
    private static final double TC = 647.096;
    private static final double PC = 220.64;

    private Steam() {
    }

    public static double satPressure(double tK) {
        double t = Math.min(tK, TC - 1e-6);
        double tau = 1.0 - t / TC;
        double s = -7.85951783 * tau + 1.84408259 * Math.pow(tau, 1.5) - 11.7866497 * Math.pow(tau, 3)
                + 22.6807411 * Math.pow(tau, 3.5) - 15.9618719 * Math.pow(tau, 4) + 1.80122502 * Math.pow(tau, 7.5);
        return PC * Math.exp(TC / t * s);
    }

    public static double satTemp(double pBar) {
        double p = Math.max(0.01, Math.min(pBar, PC - 0.01));
        double lo = 273.0, hi = TC - 1e-3;
        for (int i = 0; i < 60; i++) {
            double mid = 0.5 * (lo + hi);
            if (satPressure(mid) < p)
                lo = mid;
            else
                hi = mid;
        }
        return 0.5 * (lo + hi);
    }

    public static double latentHeatKJ(double tK) {
        double r = Math.max(0.0, (1.0 - tK / TC) / (1.0 - 373.15 / TC));
        return 2257.0 * Math.pow(r, 0.38);
    }

    public static double densityRatio(double pBar) {
        double x = Math.max(0.0, Math.min(1.0, pBar / PC));
        return 1600.0 * Math.pow(1.0 - x, 3.2) + 1.0 + 6.0 * (1.0 - x);
    }

    public static double voidFraction(double quality, double pBar) {
        double q = Math.max(0.0, Math.min(1.0, quality));
        double k = densityRatio(pBar);
        return q * k / (1.0 + (k - 1.0) * q);
    }
}
