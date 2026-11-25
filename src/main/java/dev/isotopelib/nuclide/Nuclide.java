package dev.isotopelib.nuclide;

import javax.annotation.Nullable;

/**
 * A radionuclide.
 *
 * @param name            element and mass number, e.g. "Cs-137"
 * @param massNumber      mass number, also used as the atomic mass
 * @param halfLifeSeconds half-life
 * @param mode            how it decays
 * @param energyMeV       mean energy left in the material per decay, short-lived daughters that are not in the library included
 * @param gammaShare      share of that energy carried by gamma rays
 * @param daughter        what it turns into, null when that is stable or not followed
 */
public record Nuclide(String name, int massNumber, double halfLifeSeconds, Mode mode, double energyMeV, double gammaShare, @Nullable String daughter) {
    public static final double AVOGADRO = 6.02214076e23, JOULE_PER_MEV = 1.602176634e-13;

    public enum Mode {
        ALPHA, BETA
    }

    /** Decay constant, 1/s. */
    public double lambda() {
        return Math.log(2) / halfLifeSeconds;
    }

    public double activityBqPerGram() {
        return lambda() * AVOGADRO / massNumber;
    }

    public double wattsPerGram() {
        return activityBqPerGram() * energyMeV * JOULE_PER_MEV;
    }
}
