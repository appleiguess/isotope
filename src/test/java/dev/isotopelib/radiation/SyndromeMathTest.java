package dev.isotopelib.radiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Phase lengths follow the shape of the CDC ARS fact sheet: higher dose, earlier and shorter latent phase, none at the top. */
class SyndromeMathTest {
    @Test
    void prodromalBoundaries() {
        assertEquals(2.0, SyndromeMath.prodromalMinutes(1.0), 1e-9);
        assertEquals(0.5, SyndromeMath.prodromalMinutes(6.0), 1e-9);
        assertEquals(0.5, SyndromeMath.prodromalMinutes(20.0), 1e-9);
        assertEquals(2.0, SyndromeMath.prodromalMinutes(0.5), 1e-9);
    }

    @Test
    void latentBoundaries() {
        assertEquals(15.0, SyndromeMath.latentMinutes(1.0), 1e-9);
        assertEquals(15.0, SyndromeMath.latentMinutes(1.9), 1e-9);
        assertEquals(8.0, SyndromeMath.latentMinutes(2.0), 1e-9);
        assertEquals(8.0, SyndromeMath.latentMinutes(3.9), 1e-9);
        assertEquals(4.0, SyndromeMath.latentMinutes(4.0), 1e-9);
        assertEquals(4.0, SyndromeMath.latentMinutes(5.9), 1e-9);
        assertEquals(1.0, SyndromeMath.latentMinutes(6.0), 1e-9);
        assertEquals(1.0, SyndromeMath.latentMinutes(7.9), 1e-9);
        assertEquals(0.0, SyndromeMath.latentMinutes(8.1), 1e-9);
        assertEquals(0.0, SyndromeMath.latentMinutes(50.0), 1e-9);
    }

    @Test
    void moreDoseNeverLengthensAPhase() {
        double prevP = Double.MAX_VALUE, prevL = Double.MAX_VALUE;
        for (double sv = 1.0; sv <= 12.0; sv += 0.01) {
            double p = SyndromeMath.prodromalMinutes(sv), l = SyndromeMath.latentMinutes(sv);
            assertTrue(p <= prevP + 1e-12, "prodromal at " + sv);
            assertTrue(l <= prevL + 1e-12, "latent at " + sv);
            prevP = p;
            prevL = l;
        }
    }

    @Test
    void speedScalesTicks() {
        assertEquals(2400, SyndromeMath.ticks(2.0, 1.0));
        assertEquals(240, SyndromeMath.ticks(2.0, 10.0));
        assertEquals(24000, SyndromeMath.ticks(2.0, 0.1));
    }

    @Test
    void rescaleNeverLengthens() {
        assertEquals(1000, SyndromeMath.rescaleRemaining(1000, 15, 15));
        assertEquals(500, SyndromeMath.rescaleRemaining(1000, 15, 7.5));
        assertEquals(1000, SyndromeMath.rescaleRemaining(1000, 4, 8));
        assertEquals(0, SyndromeMath.rescaleRemaining(1000, 4, 0));
    }

    @Test
    void healthPenalty() {
        assertEquals(0.0, SyndromeMath.maxHealthPenalty(1000), 1e-9);
        assertEquals(4.0, SyndromeMath.maxHealthPenalty(3000), 1e-9);
        assertEquals(12.0, SyndromeMath.maxHealthPenalty(7000), 1e-9);
        assertEquals(12.0, SyndromeMath.maxHealthPenalty(20000), 1e-9);
    }
}
