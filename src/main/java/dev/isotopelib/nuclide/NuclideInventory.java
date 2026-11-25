package dev.isotopelib.nuclide;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.isotopelib.nuclide.Nuclide.Mode;

/**
 * An amount of radioactive material by nuclide, in atoms. Decay is solved exactly along each chain (Bateman), so one step of a
 * thousand years gives the same result as a thousand steps of one year.
 */
public final class NuclideInventory {
    private static final int MAX_CHAIN = 12;

    private final Map<String, Double> atoms = new LinkedHashMap<>();

    public NuclideInventory addGrams(Nuclide n, double grams) {
        return addAtoms(n, grams / n.massNumber() * Nuclide.AVOGADRO);
    }

    public NuclideInventory addActivity(Nuclide n, double bq) {
        return addAtoms(n, bq / n.lambda());
    }

    public NuclideInventory addAtoms(Nuclide n, double count) {
        if (count > 0)
            atoms.merge(n.name(), count, Double::sum);
        return this;
    }

    public void addAll(NuclideInventory other, double share) {
        other.atoms.forEach((k, v) -> atoms.merge(k, v * share, Double::sum));
    }

    /** Takes a share (0 to 1) of everything out. */
    public NuclideInventory take(double share) {
        double s = Math.max(0, Math.min(1, share));
        NuclideInventory out = new NuclideInventory();
        out.addAll(this, s);
        atoms.replaceAll((k, v) -> v * (1 - s));
        atoms.values().removeIf(v -> v <= 0);
        return out;
    }

    public double atoms(Nuclide n) {
        return atoms.getOrDefault(n.name(), 0.0);
    }

    public double grams(Nuclide n) {
        return atoms(n) * n.massNumber() / Nuclide.AVOGADRO;
    }

    public double activityBq(Nuclide n) {
        return atoms(n) * n.lambda();
    }

    public double activityBq() {
        return sum(n -> 1.0);
    }

    /** Decay heat in watts. */
    public double heatWatts() {
        return sum(n -> n.energyMeV() * Nuclide.JOULE_PER_MEV);
    }

    /** Share of the decay energy carried by alpha, beta and gamma. */
    public double[] energyShares() {
        double all = sum(Nuclide::energyMeV);
        if (all <= 0)
            return new double[3];
        double alpha = sum(n -> n.mode() == Mode.ALPHA ? n.energyMeV() * (1 - n.gammaShare()) : 0);
        double gamma = sum(n -> n.energyMeV() * n.gammaShare());
        return new double[]{alpha / all, (all - alpha - gamma) / all, gamma / all};
    }

    private double sum(java.util.function.ToDoubleFunction<Nuclide> perDecay) {
        double s = 0;
        for (Map.Entry<String, Double> e : atoms.entrySet()) {
            Nuclide n = Nuclides.get(e.getKey());
            if (n != null)
                s += e.getValue() * n.lambda() * perDecay.applyAsDouble(n);
        }
        return s;
    }

    public boolean isEmpty() {
        return atoms.isEmpty();
    }

    public Map<String, Double> view() {
        return Collections.unmodifiableMap(atoms);
    }

    /** Lets time pass. */
    public void decay(double seconds) {
        if (seconds <= 0 || atoms.isEmpty())
            return;
        Map<String, Double> start = new LinkedHashMap<>(atoms);
        atoms.clear();
        for (Map.Entry<String, Double> e : start.entrySet()) {
            Nuclide n = Nuclides.get(e.getKey());
            if (n == null) {
                atoms.merge(e.getKey(), e.getValue(), Double::sum);
                continue;
            }
            double[] lambda = new double[MAX_CHAIN];
            double product = e.getValue();
            for (int len = 1; n != null && len <= MAX_CHAIN; len++) {
                lambda[len - 1] = n.lambda();
                double left = product * chainFactor(Arrays.copyOf(lambda, len), seconds);
                if (left >= 1)
                    atoms.merge(n.name(), left, Double::sum);
                product *= n.lambda();
                n = n.daughter() == null ? null : Nuclides.get(n.daughter());
            }
        }
    }

    /**
     * Bateman without the products of lambdas: (-1)^(n-1) times the divided difference of exp(-lambda t) over the decay constants.
     * Divided differences do not depend on the order, so the constants are sorted, which keeps the differences well conditioned.
     */
    static double chainFactor(double[] lambda, double t) {
        int n = lambda.length;
        Arrays.sort(lambda);
        if (n == 1)
            return Math.exp(-lambda[0] * t);
        if (lambda[n - 1] * t < 1e-3) {
            double mean = Arrays.stream(lambda).average().orElse(0), f = 1;
            for (int i = 1; i < n; i++)
                f *= t / i;
            return f * Math.exp(-mean * t);
        }
        double[] f = new double[n - 1];
        for (int i = 0; i < n - 1; i++)
            f[i] = firstDifference(lambda[i], lambda[i + 1], t);
        for (int order = 2; order < n; order++)
            for (int i = 0; i + order < n; i++)
                f[i] = (f[i] - f[i + 1]) / (lambda[i + order] - lambda[i]);
        return Math.max(0, f[0]);
    }

    /** (exp(-a t) - exp(-b t)) / (b - a) without losing digits when a and b are close. */
    private static double firstDifference(double a, double b, double t) {
        double d = b - a;
        if (d * t < 1e-12)
            return t * Math.exp(-a * t);
        return Math.exp(-a * t) * -Math.expm1(-d * t) / d;
    }
}
