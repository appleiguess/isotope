package dev.isotopelib.nuclide;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.isotopelib.nuclide.Nuclide.Mode;

/**
 * The nuclides that matter in a reactor, its waste and its fallout. Half-lives are the measured ones; decay energies are rounded
 * and good to about ten percent. Other mods may add theirs with {@link #register}.
 */
public final class Nuclides {
    private static final double MIN = 60, H = 3600, D = 24 * H, Y = 365.25 * D;
    private static final Map<String, Nuclide> ALL = new LinkedHashMap<>();

    // fission products
    public static final Nuclide KR85 = beta("Kr-85", 85, 10.74 * Y, 0.25, 0.01, null);
    public static final Nuclide SR90 = beta("Sr-90", 90, 28.8 * Y, 0.196, 0, "Y-90");
    public static final Nuclide Y90 = beta("Y-90", 90, 64.0 * H, 0.933, 0, null);
    public static final Nuclide ZR95 = beta("Zr-95", 95, 64.0 * D, 0.85, 0.86, "Nb-95");
    public static final Nuclide NB95 = beta("Nb-95", 95, 35.0 * D, 0.81, 0.94, null);
    public static final Nuclide MO99 = beta("Mo-99", 99, 65.9 * H, 0.54, 0.28, "Tc-99m");
    public static final Nuclide TC99M = beta("Tc-99m", 99, 6.01 * H, 0.142, 0.89, null);
    public static final Nuclide RU106 = beta("Ru-106", 106, 371.8 * D, 1.63, 0.13, null);
    public static final Nuclide TE132 = beta("Te-132", 132, 3.20 * D, 0.33, 0.69, "I-132");
    public static final Nuclide I132 = beta("I-132", 132, 2.295 * H, 2.77, 0.82, null);
    public static final Nuclide I131 = beta("I-131", 131, 8.02 * D, 0.572, 0.67, null);
    public static final Nuclide I135 = beta("I-135", 135, 6.57 * H, 1.94, 0.81, "Xe-135");
    public static final Nuclide XE133 = beta("Xe-133", 133, 5.247 * D, 0.181, 0.25, null);
    public static final Nuclide XE135 = beta("Xe-135", 135, 9.14 * H, 0.567, 0.44, null);
    public static final Nuclide CS134 = beta("Cs-134", 134, 2.065 * Y, 1.72, 0.90, null);
    public static final Nuclide CS137 = beta("Cs-137", 137, 30.08 * Y, 0.188, 0, "Ba-137m");
    public static final Nuclide BA137M = beta("Ba-137m", 137, 2.552 * MIN, 0.662, 0.90, null);
    public static final Nuclide CE144 = beta("Ce-144", 144, 284.9 * D, 1.35, 0.04, null);
    public static final Nuclide PM147 = beta("Pm-147", 147, 2.623 * Y, 0.062, 0, null);

    // activation products
    public static final Nuclide H3 = beta("H-3", 3, 12.32 * Y, 0.0057, 0, null);
    public static final Nuclide C14 = beta("C-14", 14, 5700 * Y, 0.0495, 0, null);
    public static final Nuclide CO60 = beta("Co-60", 60, 5.271 * Y, 2.60, 0.96, null);

    // uranium
    public static final Nuclide U234 = alpha("U-234", 234, 2.455e5 * Y, 4.86, null);
    public static final Nuclide U235 = alpha("U-235", 235, 7.04e8 * Y, 4.68, null);
    public static final Nuclide U238 = alpha("U-238", 238, 4.468e9 * Y, 4.27, "Th-234");
    public static final Nuclide TH234 = beta("Th-234", 234, 24.1 * D, 0.07, 0.15, "Pa-234m");
    public static final Nuclide PA234M = beta("Pa-234m", 234, 1.16 * MIN, 0.83, 0.02, "U-234");

    // transuranics
    public static final Nuclide NP239 = beta("Np-239", 239, 2.356 * D, 0.41, 0.43, "Pu-239");
    public static final Nuclide PU238 = alpha("Pu-238", 238, 87.7 * Y, 5.59, "U-234");
    public static final Nuclide PU239 = alpha("Pu-239", 239, 24110 * Y, 5.24, "U-235");
    public static final Nuclide PU240 = alpha("Pu-240", 240, 6561 * Y, 5.26, null);
    public static final Nuclide PU241 = beta("Pu-241", 241, 14.29 * Y, 0.0054, 0, "Am-241");
    public static final Nuclide AM241 = alpha("Am-241", 241, 432.6 * Y, 5.64, null);
    public static final Nuclide CM244 = alpha("Cm-244", 244, 18.11 * Y, 5.90, "Pu-240");

    private Nuclides() {
    }

    private static Nuclide beta(String name, int a, double halfLife, double mev, double gamma, String daughter) {
        return register(new Nuclide(name, a, halfLife, Mode.BETA, mev, gamma, daughter));
    }

    private static Nuclide alpha(String name, int a, double halfLife, double mev, String daughter) {
        return register(new Nuclide(name, a, halfLife, Mode.ALPHA, mev, 0.002, daughter));
    }

    public static synchronized Nuclide register(Nuclide n) {
        if (n.halfLifeSeconds() <= 0)
            throw new IllegalArgumentException(n.name() + ": half-life must be positive");
        if (ALL.putIfAbsent(n.name(), n) != null)
            throw new IllegalArgumentException(n.name() + " is already registered");
        return n;
    }

    /** Null when there is no such nuclide. */
    public static Nuclide get(String name) {
        return ALL.get(name);
    }

    public static Collection<Nuclide> all() {
        return Collections.unmodifiableCollection(ALL.values());
    }
}
