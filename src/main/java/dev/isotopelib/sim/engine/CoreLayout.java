package dev.isotopelib.sim.engine;

public final class CoreLayout {
    public static final byte COOLANT = 0, GRAPHITE = 1, FUEL = 2, ROD = 3;

    public static final int MAX = 13;

    public static final double REF_PEAKING = 1.40;

    static final double K_MOD = 2.5, K_LEAK = 2.0;
    static final double OPT_MOD = 0.75;

    private static final class Ref {
        static final Raw[] OF = {raw(3, 4, 3, referenceCells(false), false), raw(3, 4, 3, referenceCells(true), true)};
    }

    public final int width, depth;
    public final double moderation, leakage, radialPeaking, rodCoverage, reactivityDollars;

    public final byte[] columnKind;

    public final float[] columnPower;

    public final boolean[] uncovered;

    private CoreLayout(int w, int depth, double moderation, double leakage, double radialPeaking, double rodCoverage,
                       double reactivityDollars, byte[] columnKind, float[] columnPower, boolean[] uncovered) {
        this.width = w;
        this.depth = depth;
        this.moderation = moderation;
        this.leakage = leakage;
        this.radialPeaking = radialPeaking;
        this.rodCoverage = rodCoverage;
        this.reactivityDollars = reactivityDollars;
        this.columnKind = columnKind;
        this.columnPower = columnPower;
        this.uncovered = uncovered;
    }

    public static CoreLayout reference() {
        return new CoreLayout(0, 0, Ref.OF[0].moderation, Ref.OF[0].leakage, REF_PEAKING, 1.0, 0.0, new byte[0], new float[0], new boolean[0]);
    }

    public static CoreLayout of(int w, int h, int d, byte[] cells, boolean[] rodColumn, boolean graphite) {
        Raw r = raw(w, h, d, cells, graphite);
        if (r.fuelCells == 0)
            return new CoreLayout(w, d, 0, 0, REF_PEAKING, 0, 0, r.kind, new float[w * d], new boolean[w * d]);
        Raw ref = Ref.OF[graphite ? 1 : 0];
        double rho = K_MOD * (lattice(r.moderation) - lattice(ref.moderation)) - K_LEAK * (r.leakage - ref.leakage);
        rho = Math.max(-3.0, Math.min(1.0, rho));

        int fuelCols = 0, covered = 0;
        boolean[] uncovered = new boolean[w * d];
        float[] rel = new float[w * d];
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                int i = x + z * w;
                if (r.colPower[i] <= 0)
                    continue;
                fuelCols++;
                rel[i] = (float) (r.colPower[i] / r.mean);
                if (reached(x, z, w, d, rodColumn))
                    covered++;
                else
                    uncovered[i] = true;
            }
        }

        double peaking = fuelCols == 1 ? 1.05
                : Math.max(1.05, Math.min(2.4, 1.0 + (REF_PEAKING - 1.0) * (r.ratio - 1.0) / (ref.ratio - 1.0)));
        return new CoreLayout(w, d, r.moderation, r.leakage, peaking, covered / (double) fuelCols, rho, r.kind, rel, uncovered);
    }

    private record Raw(int fuelCells, double moderation, double leakage, double[] colPower, double mean, double ratio, byte[] kind) {
    }

    private static Raw raw(int w, int h, int d, byte[] cells, boolean graphite) {
        double modSum = 0, leakSum = 0;
        int fuelCells = 0;
        double[] colPower = new double[w * d];
        byte[] kind = new byte[w * d];
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < d; z++) {
                for (int x = 0; x < w; x++) {
                    byte c = cells[x + z * w + y * w * d];
                    int col = x + z * w;
                    if (rank(c) > rank(kind[col]))
                        kind[col] = c;
                    if (c != FUEL)
                        continue;
                    double mod = 0, leak = 0;
                    int adjFuel = 0, diagFuel = 0;
                    for (int[] o : ADJ) {
                        int nx = x + o[0], nz = z + o[1];
                        if (nx < 0 || nz < 0 || nx >= w || nz >= d) {
                            leak += 0.25;
                            continue;
                        }
                        byte n = cells[nx + nz * w + y * w * d];
                        mod += 0.25 * moderating(n, graphite);
                        if (n == FUEL)
                            adjFuel++;
                    }
                    for (int[] o : DIAG) {
                        int nx = x + o[0], nz = z + o[1];
                        if (nx >= 0 && nz >= 0 && nx < w && nz < d && cells[nx + nz * w + y * w * d] == FUEL)
                            diagFuel++;
                    }
                    modSum += mod;
                    leakSum += leak;
                    fuelCells++;

                    colPower[col] += (1.0 + 0.25 * adjFuel + 0.1 * diagFuel) * (1.0 - 0.5 * leak) * (0.8 + 0.4 * mod);
                }
            }
        }
        double mean = 0, max = 0;
        int cols = 0;
        for (double p : colPower) {
            if (p <= 0)
                continue;
            cols++;
            mean += p;
            max = Math.max(max, p);
        }
        mean = cols == 0 ? 1.0 : mean / cols;
        return new Raw(fuelCells, fuelCells == 0 ? 0 : modSum / fuelCells, fuelCells == 0 ? 0 : leakSum / fuelCells, colPower, mean,
                max / mean, kind);
    }

    public static byte[] referenceCells(boolean graphite) {
        int w = 3, h = 4, d = 3;
        byte[] c = new byte[w * h * d];
        for (int y = 0; y < h; y++)
            for (int z = 0; z < d; z++)
                for (int x = 0; x < w; x++) {
                    boolean cross = x == 1 || z == 1;
                    byte v;
                    if (graphite)
                        v = cross ? FUEL : ROD;
                    else if (cross)
                        v = y <= 2 ? FUEL : COOLANT;
                    else
                        v = y <= 1 ? GRAPHITE : COOLANT;
                    c[x + z * w + y * w * d] = v;
                }
        return c;
    }

    static double lattice(double m) {
        if (m > OPT_MOD) {
            double t = (m - OPT_MOD) / (1.0 - OPT_MOD);
            return 1.0 - 0.15 * t * t;
        }
        double t = 1.0 - m / OPT_MOD;
        return 1.0 - t * t;
    }

    private static double moderating(byte n, boolean graphite) {
        if (graphite)
            return n == GRAPHITE || n == ROD ? 1.0 : n == FUEL ? 0.5 : 0.0;
        return n == COOLANT || n == GRAPHITE ? 1.0 : n == ROD ? 0.5 : 0.0;
    }

    private static boolean reached(int x, int z, int w, int d, boolean[] rodColumn) {
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, nz = z + dz;
                if (nx >= 0 && nz >= 0 && nx < w && nz < d && rodColumn[nx + nz * w])
                    return true;
            }
        return false;
    }

    private static int rank(byte c) {
        return switch (c) {
            case FUEL -> 3;
            case ROD -> 2;
            case GRAPHITE -> 1;
            default -> 0;
        };
    }

    public double rodWorthFactor() {
        return 0.3 + 0.7 * rodCoverage;
    }

    public double chfFactor() {
        return REF_PEAKING / radialPeaking;
    }

    private static final int[][] ADJ = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAG = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
}
