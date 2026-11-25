package dev.isotopelib.fallout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * Fallout on the ground, one value per chunk: drifts with the wind, spreads and fades. It does not care what released it, a
 * reactor or a bomb. Only chunks with fallout are stored. Pure Java.
 */
public final class FalloutField {
    public static final int CELL = 16;
    /** How far from a release fallout may get, in chunks (about 500 blocks). */
    public static final int MAX_RADIUS = 31;
    /** Cell size of the field in Going Nuclear before 0.612. */
    public static final int LEGACY_CELL = 20;

    private static final double SIGMA = 2.75;
    private static final int RELEASE_RADIUS = 8;
    private static final float ADVECTION = 0.2f, DIFFUSION = 0.019f, FADE = 0.9975f;
    private static final float MIN = 0.4f;

    private final Map<Long, Float> cells = new HashMap<>();
    private final List<long[]> sources = new ArrayList<>();
    private float windAngle = 0.7f, windSpeed = 0.5f;

    public static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    public static int keyX(long k) {
        return (int) (k >> 32);
    }

    public static int keyZ(long k) {
        return (int) k;
    }

    public boolean isEmpty() {
        return cells.isEmpty();
    }

    public float cell(int cx, int cz) {
        return cells.getOrDefault(key(cx, cz), 0f);
    }

    /** Strength at block coordinates, interpolated between the four nearest chunk centres. */
    public double at(double x, double z) {
        return sample(cells, x, z, CELL);
    }

    private static double sample(Map<Long, Float> map, double x, double z, int size) {
        if (map.isEmpty())
            return 0;
        double fx = x / size - 0.5, fz = z / size - 0.5;
        int x0 = (int) Math.floor(fx), z0 = (int) Math.floor(fz);
        double tx = fx - x0, tz = fz - z0;
        double a = map.getOrDefault(key(x0, z0), 0f), b = map.getOrDefault(key(x0 + 1, z0), 0f);
        double c = map.getOrDefault(key(x0, z0 + 1), 0f), d = map.getOrDefault(key(x0 + 1, z0 + 1), 0f);
        double north = a + (b - a) * tx, south = c + (d - c) * tx;
        return north + (south - north) * tz;
    }

    /** Releases fallout at block coordinates; strength is the value at the centre. */
    public void release(int x, int z, double strength) {
        sources.add(new long[]{x, z});
        int ccx = Math.floorDiv(x, CELL), ccz = Math.floorDiv(z, CELL);
        for (int dx = -RELEASE_RADIUS; dx <= RELEASE_RADIUS; dx++)
            for (int dz = -RELEASE_RADIUS; dz <= RELEASE_RADIUS; dz++) {
                float v = (float) (strength * Math.exp(-(dx * dx + dz * dz) / (2 * SIGMA * SIGMA)));
                if (v > 0.5f)
                    cells.merge(key(ccx + dx, ccz + dz), v, Float::sum);
            }
    }

    /** Removes the fallout within a radius in blocks. Returns how many chunks were cleared. */
    public int clearAround(double x, double z, int radius) {
        int before = cells.size();
        cells.keySet().removeIf(k -> Math.hypot((keyX(k) + 0.5) * CELL - x, (keyZ(k) + 0.5) * CELL - z) <= radius);
        if (cells.isEmpty())
            sources.clear();
        return before - cells.size();
    }

    /** One step, meant for every 100 ticks. random gives numbers in [0, 1). */
    public void step(DoubleSupplier random) {
        if (cells.isEmpty())
            return;
        windAngle += (float) (random.getAsDouble() - 0.5) * 0.12f;
        windSpeed = Math.max(0.15f, Math.min(0.9f, windSpeed + (float) (random.getAsDouble() - 0.5) * 0.06f));
        double ox = Math.cos(windAngle), oz = Math.sin(windAngle), sum = Math.abs(ox) + Math.abs(oz);
        float adv = ADVECTION * windSpeed;
        float ax = (float) (adv * Math.abs(ox) / sum), az = (float) (adv * Math.abs(oz) / sum);
        int sx = ox >= 0 ? 1 : -1, sz = oz >= 0 ? 1 : -1;
        float stay = 1f - adv - 4 * DIFFUSION;

        Map<Long, Float> next = new HashMap<>(cells.size() * 2);
        for (Map.Entry<Long, Float> e : cells.entrySet()) {
            int cx = keyX(e.getKey()), cz = keyZ(e.getKey());
            float v = e.getValue();
            next.merge(e.getKey(), v * stay, Float::sum);
            next.merge(key(cx + sx, cz), v * ax, Float::sum);
            next.merge(key(cx, cz + sz), v * az, Float::sum);
            next.merge(key(cx + 1, cz), v * DIFFUSION, Float::sum);
            next.merge(key(cx - 1, cz), v * DIFFUSION, Float::sum);
            next.merge(key(cx, cz + 1), v * DIFFUSION, Float::sum);
            next.merge(key(cx, cz - 1), v * DIFFUSION, Float::sum);
        }
        cells.clear();
        next.forEach((k, v) -> {
            if (v * FADE >= MIN && inRange(keyX(k), keyZ(k)))
                cells.put(k, v * FADE);
        });
        if (cells.isEmpty())
            sources.clear();
    }

    private boolean inRange(int cx, int cz) {
        for (long[] s : sources)
            if (Math.abs(cx - Math.floorDiv(s[0], (long) CELL)) <= MAX_RADIUS && Math.abs(cz - Math.floorDiv(s[1], (long) CELL)) <= MAX_RADIUS)
                return true;
        return false;
    }

    public Map<Long, Float> cells() {
        return cells;
    }

    /** Block x and z of every release. */
    public List<long[]> sources() {
        return sources;
    }

    public float windAngle() {
        return windAngle;
    }

    public float windSpeed() {
        return windSpeed;
    }

    /** Wind as a vector of blocks, length windSpeed. */
    public float windX() {
        return (float) Math.cos(windAngle) * windSpeed;
    }

    public float windZ() {
        return (float) Math.sin(windAngle) * windSpeed;
    }

    public void setWind(float angle, float speed) {
        windAngle = angle;
        windSpeed = speed;
    }

    /** The field of Going Nuclear before 0.612 (20 block cells) on the chunk grid. Strengths stay what they were. */
    public static FalloutField fromLegacy(Map<Long, Float> old, List<long[]> sources, float windAngle, float windSpeed) {
        FalloutField f = new FalloutField();
        f.sources.addAll(sources);
        f.setWind(windAngle, windSpeed);
        for (long k : old.keySet()) {
            int x0 = Math.floorDiv((keyX(k) - 1) * LEGACY_CELL, CELL), x1 = Math.floorDiv((keyX(k) + 2) * LEGACY_CELL, CELL);
            int z0 = Math.floorDiv((keyZ(k) - 1) * LEGACY_CELL, CELL), z1 = Math.floorDiv((keyZ(k) + 2) * LEGACY_CELL, CELL);
            for (int cx = x0; cx <= x1; cx++)
                for (int cz = z0; cz <= z1; cz++) {
                    float v = (float) sample(old, (cx + 0.5) * CELL, (cz + 0.5) * CELL, LEGACY_CELL);
                    if (v >= MIN)
                        f.cells.put(key(cx, cz), v);
                }
        }
        return f;
    }
}
