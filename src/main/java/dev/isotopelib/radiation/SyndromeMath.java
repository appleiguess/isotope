package dev.isotopelib.radiation;

/**
 * Timing of acute radiation syndrome, without any Minecraft in it. Real course (CDC fact sheet on ARS): prodromal phase with
 * nausea and vomiting, then a latent phase in which the patient feels well, then the manifest illness. The higher the dose,
 * the sooner it starts and the shorter the latent phase; at the highest doses there is none.
 */
public final class SyndromeMath {
    /** Phases of the course. The ordinal is what is saved. */
    public enum Phase {
        NONE, PRODROMAL, LATENT, MANIFEST;

        public static Phase of(int id) {
            Phase[] all = values();
            return id < 0 || id >= all.length ? NONE : all[id];
        }
    }

    /** The course begins at this dose, mSv. */
    public static final float START_MSV = 1000f;
    /** The course ends when the dose has recovered below this, mSv. */
    public static final float END_MSV = 500f;
    /** Game ticks in one minute of play. */
    public static final double TICKS_PER_MINUTE = 20.0 * 60.0;

    private SyndromeMath() {
    }

    /** Length of the prodromal phase in minutes: 2 at 1 Sv, falling linearly to 0.5 at 6 Sv and above. */
    public static double prodromalMinutes(double peakSv) {
        double t = Math.max(0.0, Math.min(1.0, (peakSv - 1.0) / 5.0));
        return 2.0 - 1.5 * t;
    }

    /** Length of the latent phase in minutes: 15 for 1-2 Sv, 8 for 2-4, 4 for 4-6, 1 for 6-8, none from 8 Sv up. */
    public static double latentMinutes(double peakSv) {
        if (peakSv >= 8.0)
            return 0.0;
        if (peakSv >= 6.0)
            return 1.0;
        if (peakSv >= 4.0)
            return 4.0;
        if (peakSv >= 2.0)
            return 8.0;
        return 15.0;
    }

    /** Ticks for a length in minutes at the given speed (1 = as listed, 10 = ten times faster). */
    public static long ticks(double minutes, double speed) {
        return Math.max(0L, Math.round(minutes * TICKS_PER_MINUTE / Math.max(1.0e-3, speed)));
    }

    /**
     * Remaining ticks of a phase after the peak dose went up: scaled by the new length over the old one, never longer than it was.
     */
    public static long rescaleRemaining(long remainingTicks, double oldPhaseMinutes, double newPhaseMinutes) {
        if (remainingTicks <= 0 || oldPhaseMinutes <= 0)
            return Math.max(0L, remainingTicks);
        long scaled = Math.round(remainingTicks * (newPhaseMinutes / oldPhaseMinutes));
        return Math.max(0L, Math.min(remainingTicks, scaled));
    }

    /** Loss of maximum health in half hearts: 2 per Sv above 1 Sv, at most 12. */
    public static double maxHealthPenalty(double doseMsv) {
        return Math.min(12.0, Math.max(0.0, 2.0 * (doseMsv / 1000.0 - 1.0)));
    }
}
