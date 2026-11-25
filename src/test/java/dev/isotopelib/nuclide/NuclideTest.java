package dev.isotopelib.nuclide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.isotopelib.radiation.RadiationMath;

class NuclideTest {
    private static final double YEAR = 365.25 * 86400;

    private static void near(double expected, double actual, double relative) {
        assertEquals(expected, actual, Math.abs(expected) * relative);
    }

    /** Handbook values. */
    @Test
    void specificActivities() {
        near(1.244e4, Nuclides.U238.activityBqPerGram(), 0.01);
        near(8.0e4, Nuclides.U235.activityBqPerGram(), 0.01);
        near(2.30e9, Nuclides.PU239.activityBqPerGram(), 0.01);
        near(3.21e12, Nuclides.CS137.activityBqPerGram(), 0.01);
        near(4.6e15, Nuclides.I131.activityBqPerGram(), 0.01);
        near(1.27e11, Nuclides.AM241.activityBqPerGram(), 0.01);
    }

    @Test
    void decayHeat() {
        near(0.57, Nuclides.PU238.wattsPerGram(), 0.03);
        near(1.93e-3, Nuclides.PU239.wattsPerGram(), 0.03);
        near(0.114, Nuclides.AM241.wattsPerGram(), 0.03);
        near(17.5, Nuclides.CO60.wattsPerGram(), 0.05);
    }

    @Test
    void daughtersAreKnownAndChainsEnd() {
        for (Nuclide n : Nuclides.all()) {
            int steps = 0;
            for (Nuclide k = n; k.daughter() != null; k = Nuclides.get(k.daughter())) {
                assertNotNull(Nuclides.get(k.daughter()), k.name());
                assertTrue(++steps < 10, n.name());
            }
        }
    }

    @Test
    void halfLife() {
        NuclideInventory inv = new NuclideInventory().addActivity(Nuclides.I131, 1e12);
        inv.decay(Nuclides.I131.halfLifeSeconds());
        near(5e11, inv.activityBq(), 1e-9);
    }

    /** Ba-137m follows its parent within minutes. */
    @Test
    void secularEquilibrium() {
        NuclideInventory inv = new NuclideInventory().addActivity(Nuclides.CS137, 1e10);
        inv.decay(3600);
        near(inv.activityBq(Nuclides.CS137), inv.activityBq(Nuclides.BA137M), 1e-3);
    }

    /** Fresh uranium grows its beta emitters in over months: Th-234 and Pa-234m end up as active as the U-238. */
    @Test
    void uraniumSeries() {
        NuclideInventory inv = new NuclideInventory().addGrams(Nuclides.U238, 1000);
        inv.decay(1);
        assertTrue(inv.activityBq(Nuclides.PA234M) < 10);
        inv.decay(YEAR);
        near(inv.activityBq(Nuclides.U238), inv.activityBq(Nuclides.TH234), 1e-3);
        near(inv.activityBq(Nuclides.U238), inv.activityBq(Nuclides.PA234M), 1e-3);
        near(Nuclides.U238.lambda() * YEAR * 0.905, inv.atoms(Nuclides.U234) / inv.atoms(Nuclides.U238), 0.01);
    }

    /** Am-241 grows in from Pu-241 and peaks after about 70 years. */
    @Test
    void americiumGrowsIn() {
        double best = 0, at = 0;
        for (int y = 5; y <= 200; y += 5) {
            NuclideInventory inv = new NuclideInventory().addGrams(Nuclides.PU241, 1);
            inv.decay(y * YEAR);
            if (inv.grams(Nuclides.AM241) > best) {
                best = inv.grams(Nuclides.AM241);
                at = y;
            }
        }
        assertEquals(73, at, 5);
        assertEquals(0.86, best, 0.03);
    }

    @Test
    void oneBigStepEqualsManySmall() {
        NuclideInventory a = mix(), b = mix();
        a.decay(10 * YEAR);
        for (int i = 0; i < 1000; i++)
            b.decay(YEAR / 100);
        for (Nuclide n : Nuclides.all())
            if (a.activityBq(n) > 1)
                near(a.activityBq(n), b.activityBq(n), 1e-5);
    }

    @Test
    void hugeAndTinyStepsStayFinite() {
        for (double t : new double[]{1e-6, 0.05, 1, 1e3, 1e9, 1e17, 1e20}) {
            NuclideInventory inv = mix();
            inv.decay(t);
            assertTrue(Double.isFinite(inv.activityBq()) && inv.activityBq() >= 0, "t=" + t);
            assertTrue(Double.isFinite(inv.heatWatts()));
        }
    }

    @Test
    void takingAShare() {
        NuclideInventory inv = new NuclideInventory().addGrams(Nuclides.CS137, 8);
        NuclideInventory out = inv.take(0.25);
        near(2, out.grams(Nuclides.CS137), 1e-9);
        near(6, inv.grams(Nuclides.CS137), 1e-9);
    }

    @Test
    void energyShares() {
        double[] pu = new NuclideInventory().addGrams(Nuclides.PU239, 1).energyShares();
        assertTrue(pu[0] > 0.99);
        double[] co = new NuclideInventory().addGrams(Nuclides.CO60, 1).energyShares();
        assertTrue(co[2] > 0.9);
        assertEquals(1, pu[0] + pu[1] + pu[2], 1e-9);
    }

    @Test
    void doseRecovers() {
        assertEquals(0f, RadiationMath.doseAfterSecond(0f, 0, 0.05, 600));
        float d = 1000f;
        for (int i = 0; i < 600; i++)
            d = RadiationMath.doseAfterSecond(d, 0, 0, 600);
        assertEquals(500f, d, 0.5f);
        assertEquals(1f, RadiationMath.doseAfterSecond(0f, 1000, 0, 0), 1e-6f);
    }

    private static NuclideInventory mix() {
        NuclideInventory inv = new NuclideInventory();
        for (Nuclide n : Nuclides.all())
            inv.addActivity(n, 1e12);
        return inv;
    }
}
