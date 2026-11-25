package dev.isotopelib.sim.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Water and steam properties against known values. */
class SteamTest {
    @Test
    void waterBoilsAt100DegreesAtOneBar() {
        assertEquals(373.15, Steam.satTemp(1.01325), 0.3);
        assertEquals(1.01325, Steam.satPressure(373.15), 0.02);
    }

    @Test
    void reactorPressureHasTheRightSaturationTemperature() {
        // 155 bar: about 618 K (345 degrees C), 62 bar: about 551 K (a typical steam generator)
        assertEquals(618.0, Steam.satTemp(155.0), 1.5);
        assertEquals(551.0, Steam.satTemp(62.0), 1.5);
    }

    @Test
    void saturationIsMonotoneAndInvertible() {
        double last = 0;
        for (double p = 0.5; p < 200; p += 3.7) {
            double t = Steam.satTemp(p);
            assertTrue(t > last, "monotone");
            last = t;
            assertEquals(p, Steam.satPressure(t), p * 0.01 + 0.01, "round trip at " + p);
        }
    }

    @Test
    void latentHeatFallsToZeroAtTheCriticalPoint() {
        assertEquals(2257.0, Steam.latentHeatKJ(373.15), 5.0);
        assertTrue(Steam.latentHeatKJ(618.0) < 1100.0 && Steam.latentHeatKJ(618.0) > 700.0);
        assertTrue(Steam.latentHeatKJ(645.0) < 450.0);
    }

    @Test
    void voidFractionRisesWithQualityAndIsHigherAtLowPressure() {
        double prev = 0;
        for (double x = 0; x <= 1.0; x += 0.1) {
            double a = Steam.voidFraction(x, 155.0);
            assertTrue(a >= prev - 1e-9);
            prev = a;
        }
        assertEquals(0.0, Steam.voidFraction(0.0, 155.0), 1e-9);
        assertEquals(1.0, Steam.voidFraction(1.0, 155.0), 1e-9);
        assertTrue(Steam.voidFraction(0.05, 10.0) > Steam.voidFraction(0.05, 155.0), "the same quality is a bigger void at low pressure");
    }

    @Test
    void randomGeneratorIsDeterministicAndSavable() {
        Rng a = new Rng(42), b = new Rng(42);
        for (int i = 0; i < 100; i++)
            assertEquals(a.nextLong(), b.nextLong());
        Rng c = new Rng(7);
        c.next();
        long s = c.state();
        double x = c.next();
        c.setState(s);
        assertEquals(x, c.next(), 0.0);
    }
}
