package dev.isotopelib.api;

/** Dose rate at a player in microsieverts per game second, before any protection. */
public record Exposure(double gamma, double beta, double inhaled) {
    public static final Exposure NONE = new Exposure(0, 0, 0);

    public double ambient() {
        return gamma + beta + inhaled;
    }
}
