package dev.isotopelib.sim.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The player's own core: the Creator's core is the zero, other lattices gain or lose reactivity, peak and escape the rods. */
class CoreLayoutTest {
    private static boolean[] cross(int w, int d) {
        boolean[] r = new boolean[w * d];
        for (int z = 0; z < d; z++)
            for (int x = 0; x < w; x++)
                r[x + z * w] = x == w / 2 || z == d / 2;
        return r;
    }

    private static boolean[] corners3() {
        boolean[] r = new boolean[9];
        r[0] = r[2] = r[6] = r[8] = true;
        return r;
    }

    /** A box of w by h by d filled with one kind, with fuel where {@code fuel} says. */
    private static byte[] grid(int w, int h, int d, byte fill, java.util.function.BiPredicate<Integer, Integer> fuel) {
        byte[] c = new byte[w * h * d];
        for (int y = 0; y < h; y++)
            for (int z = 0; z < d; z++)
                for (int x = 0; x < w; x++)
                    c[x + z * w + y * w * d] = fuel.test(x, z) ? CoreLayout.FUEL : fill;
        return c;
    }

    @Test
    void theCreatorsCoresChangeNothing() {
        CoreLayout water = CoreLayout.of(3, 4, 3, CoreLayout.referenceCells(false), cross(3, 3), false);
        assertEquals(0.0, water.reactivityDollars, 1e-9);
        assertEquals(CoreLayout.REF_PEAKING, water.radialPeaking, 1e-9);
        assertEquals(1.0, water.rodCoverage, 1e-9);
        assertEquals(1.0, water.chfFactor(), 1e-9);
        CoreLayout rbmk = CoreLayout.of(3, 4, 3, CoreLayout.referenceCells(true), corners3(), true);
        assertEquals(0.0, rbmk.reactivityDollars, 1e-9);
        assertEquals(CoreLayout.REF_PEAKING, rbmk.radialPeaking, 1e-9);
        assertEquals(1.0, rbmk.rodCoverage, 1e-9);
    }

    @Test
    void packedFuelIsUnderModeratedAndPeaks() {
        // a 5x5 block of solid fuel: no water between the channels
        CoreLayout packed = CoreLayout.of(5, 3, 5, grid(5, 3, 5, CoreLayout.COOLANT, (x, z) -> true), new boolean[25], false);
        assertTrue(packed.reactivityDollars < -1.5, "packed: " + packed.reactivityDollars);
        assertTrue(packed.radialPeaking > CoreLayout.REF_PEAKING, "peaking " + packed.radialPeaking);
        assertEquals(0.0, packed.rodCoverage, 1e-9);
        assertEquals(0.3, packed.rodWorthFactor(), 1e-9);
    }

    @Test
    void aCheckerboardInABigVesselIsBetterThanTheCross() {
        // every other cell fuel, water between: well moderated, and a big core leaks less
        boolean[] rods = new boolean[49];
        java.util.Arrays.fill(rods, true);
        CoreLayout chess = CoreLayout.of(7, 4, 7, grid(7, 4, 7, CoreLayout.COOLANT, (x, z) -> (x + z) % 2 == 0), rods, false);
        assertTrue(chess.reactivityDollars > 0, "checkerboard: " + chess.reactivityDollars);
        assertTrue(chess.moderation > 0.7, "moderation " + chess.moderation);
        assertTrue(chess.radialPeaking < 1.6, "a big even core peaks only gently in the middle: " + chess.radialPeaking);
    }

    @Test
    void fuelFarFromTheRodsIsNotHeld() {
        // an RBMK 5 wide with rods only at one end
        boolean[] rods = new boolean[25];
        rods[0] = true;
        byte[] cells = grid(5, 2, 5, CoreLayout.GRAPHITE, (x, z) -> (x + z) % 2 == 1);
        CoreLayout c = CoreLayout.of(5, 2, 5, cells, rods, true);
        assertTrue(c.rodCoverage < 0.3, "coverage " + c.rodCoverage);
        assertTrue(c.uncovered[4 + 3 * 5]);
    }

    @Test
    void reactivityIsBounded() {
        CoreLayout one = CoreLayout.of(1, 1, 1, new byte[]{CoreLayout.FUEL}, new boolean[]{true}, false);
        assertTrue(one.reactivityDollars >= -3.0 && one.reactivityDollars <= 1.0);
        assertEquals(1.05, one.radialPeaking, 1e-9);
        CoreLayout none = CoreLayout.of(2, 2, 2, new byte[8], new boolean[4], false);
        assertEquals(0.0, none.reactivityDollars, 1e-9);
    }
}
