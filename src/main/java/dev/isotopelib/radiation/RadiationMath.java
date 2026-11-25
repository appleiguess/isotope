package dev.isotopelib.radiation;

/** Radiation formulas without any Minecraft in them. */
public final class RadiationMath {
    public static final double GAMMA_CONSTANT = 5.5e-8;

    public static final double MIN_DISTANCE = 0.3;

    /** Factor on the half value layer: alpha is stopped by almost anything, gamma is not. */
    public enum RadiationType {
        ALPHA(0.02),
        BETA(0.12),
        GAMMA(1.0);

        public final double hvlFactor;

        RadiationType(double hvlFactor) {
            this.hvlFactor = hvlFactor;
        }
    }

    private RadiationMath() {
    }

    public static double decayedActivity(double activityBq, long ageTicks, long halfLifeTicks) {
        if (halfLifeTicks <= 0)
            return activityBq;
        return activityBq * Math.pow(0.5, ageTicks / (double) halfLifeTicks);
    }

    public static double gammaDoseRate(double activityBq, double distanceMetres) {
        double r = Math.max(MIN_DISTANCE, distanceMetres);
        return GAMMA_CONSTANT * (activityBq / 1.0e6) / (r * r);
    }

    /** A stack shields its own inside. */
    public static double stackSelfShield(int count) {
        return count <= 1 ? Math.max(0, count) : Math.sqrt(count);
    }

    public static double geigerClicksPerSecond(double usvPerSecond) {
        if (usvPerSecond < 0.05)
            return 0.0;
        return Math.min(400.0, 0.3 + usvPerSecond);
    }

    /** Dose in mSv after one more second: takes the dose, repairs some and lets it fade with a half-life (0: no fading). */
    public static float doseAfterSecond(float doseMsv, double effectiveUsv, double recoveryMsv, double halfLifeSeconds) {
        double d = doseMsv + effectiveUsv / 1000.0 - recoveryMsv;
        if (halfLifeSeconds > 0)
            d *= Math.pow(0.5, 1.0 / halfLifeSeconds);
        return (float) Math.max(0, d);
    }
}
