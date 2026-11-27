package dev.isotopelib.radiation;

/**
 * Radioactive dust that sticks to a person, their clothes and their things, without any Minecraft in it.
 *
 * Fallout dust settles on whoever stands in it and keeps radiating after they leave, until it is washed off. Taking off
 * the outer layer of clothing removes most of it (CDC: up to 90 %), washing removes the rest.
 */
public final class ContaminationMath {
    /** Becquerels that settle per second in fallout of 1 µSv/s under the open sky. */
    public static final double DEPOSIT_PER_FALLOUT = 5.0e4;
    /** Share that falls off by itself every second. */
    public static final double SHED = 0.002;
    /** Share washed off per second: in water, in the rain. */
    public static final double WASH_WATER = 0.25, WASH_RAIN = 0.05;
    /** Below this it no longer counts. */
    public static final double MIN_BQ = 1.0e4;
    /** Mixed fission-product dust: mostly beta, some gamma, a little alpha that only matters when breathed in. */
    public static final double ALPHA = 0.1, BETA = 0.7, GAMMA = 0.2;
    /** Dose constants of something carried on the body, the same as for carried items (µSv/s per MBq at 1 m). */
    public static final double BETA_CONSTANT = 6.0e-7, INHALED_CONSTANT = 2.0e-7, BODY_DISTANCE = 0.5;

    private ContaminationMath() {
    }

    /** Becquerels after one more second: what was there plus what settled, less what fell or was washed off. */
    public static double afterSecond(double bq, double depositBq, double washShare) {
        double v = (bq + Math.max(0, depositBq)) * (1 - SHED) * (1 - Math.min(1, Math.max(0, washShare)));
        return v < MIN_BQ ? 0 : v;
    }

    /**
     * Dose rate in µSv per game second from dust on the body: {gamma, beta, inhaled}. {@code timeScale} is Isotope's
     * doseTimeScale, {@code breathes} false when a respirator keeps it out of the lungs.
     */
    public static double[] doseRate(double bq, double timeScale, boolean breathes) {
        if (bq <= 0)
            return new double[3];
        double mbq = timeScale * bq / 1.0e6;
        double d2 = BODY_DISTANCE * BODY_DISTANCE;
        return new double[]{RadiationMath.GAMMA_CONSTANT * mbq * GAMMA / d2, BETA_CONSTANT * mbq * BETA / d2,
                breathes ? INHALED_CONSTANT * mbq * ALPHA : 0};
    }

    /** How many specks a contaminated item shows in its slot: 1 from 10 kBq, one more every factor of 30, at most 5. */
    public static int specks(double bq) {
        if (bq < MIN_BQ)
            return 0;
        return (int) Math.min(5, 1 + Math.floor(Math.log10(bq / MIN_BQ) / Math.log10(30)));
    }
}
