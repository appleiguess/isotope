package dev.isotopelib.sim.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SimpleCoreCompressionTest {
    private static double xenonAfter(double compression) {
        SimpleCore c = new SimpleCore();
        c.setTimeCompression(compression);
        c.startAt(0.2);
        for (int i = 0; i < 1200; i++)
            c.step(1.0, false, true, 8.0, 0.25);
        return c.xenon();
    }

    /** A higher compression brings xenon to its equilibrium sooner. */
    @Test
    void compressionReachesTheSimpleCore() {
        assertTrue(xenonAfter(600) > xenonAfter(60) + 0.05);
    }
}
