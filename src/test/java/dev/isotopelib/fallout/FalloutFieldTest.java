package dev.isotopelib.fallout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

class FalloutFieldTest {
    private static double total(FalloutField f) {
        return f.cells().values().stream().mapToDouble(v -> v).sum();
    }

    @Test
    void keysSurviveNegativeChunks() {
        for (int x : new int[]{-40, -1, 0, 7})
            for (int z : new int[]{-40, -1, 0, 7}) {
                assertEquals(x, FalloutField.keyX(FalloutField.key(x, z)));
                assertEquals(z, FalloutField.keyZ(FalloutField.key(x, z)));
            }
    }

    @Test
    void releaseIsStrongestAtItsChunk() {
        FalloutField f = new FalloutField();
        f.release(-100, 200, 300);
        assertEquals(300, f.cell(Math.floorDiv(-100, 16), 200 / 16), 1e-3);
        assertTrue(f.at(-100, 200) > f.at(0, 200));
    }

    @Test
    void fadesAndEnds() {
        FalloutField f = new FalloutField();
        f.release(0, 0, 300);
        Random r = new Random(1);
        double before = total(f);
        for (int i = 0; i < 50; i++)
            f.step(r::nextDouble);
        assertTrue(total(f) < before && total(f) > before * 0.6);
        for (int i = 0; i < 20000 && !f.isEmpty(); i++)
            f.step(r::nextDouble);
        assertTrue(f.isEmpty());
        assertTrue(f.sources().isEmpty());
    }

    @Test
    void staysInRange() {
        FalloutField f = new FalloutField();
        f.release(0, 0, 5000);
        Random r = new Random(2);
        for (int i = 0; i < 600; i++)
            f.step(r::nextDouble);
        for (long k : f.cells().keySet())
            assertTrue(Math.abs(FalloutField.keyX(k)) <= FalloutField.MAX_RADIUS && Math.abs(FalloutField.keyZ(k)) <= FalloutField.MAX_RADIUS);
    }

    @Test
    void clearing() {
        FalloutField f = new FalloutField();
        f.release(0, 0, 300);
        assertTrue(f.clearAround(0, 0, 10000) > 0);
        assertTrue(f.isEmpty() && f.sources().isEmpty());
    }

    @Test
    void legacyFieldKeepsItsStrength() {
        Map<Long, Float> old = new HashMap<>();
        double oldSum = 0;
        for (int x = -6; x <= 6; x++)
            for (int z = -6; z <= 6; z++) {
                float v = (float) (300 * Math.exp(-(x * x + z * z) / (2 * 2.2 * 2.2)));
                old.put(FalloutField.key(x - 3, z + 5), v);
                oldSum += v * 400.0;
            }
        FalloutField f = FalloutField.fromLegacy(old, List.of(new long[]{-60, 100}), 1.2f, 0.4f);
        assertEquals(oldSum, total(f) * 256.0, oldSum * 0.05);
        // the finer grid rounds the very peak a little; the total above is what has to hold
        assertEquals(300, f.at(-50, 110), 25);
        assertEquals(1.2f, f.windAngle());
        assertEquals(1, f.sources().size());
    }
}
