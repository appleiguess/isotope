package dev.isotopelib.sim.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** MOX fuel: fewer delayed neutrons, weaker rods, and therefore a reactor that answers a rod step much more sharply. */
class MoxTest {
    private static double betaSum(PlantConfig cfg) {
        double s = 0;
        for (double b : cfg.beta)
            s += b;
        return s;
    }

    private static CoreModel core(double mox) {
        CoreModel c = new CoreModel(PlantConfig.standard());
        c.startAtPower(1.0, 155.0);
        c.setMox(mox);
        return c;
    }

    @Test
    void moxLowersTheDelayedFraction() {
        CoreModel u = core(0.0), m = core(1.0);
        assertEquals(betaSum(u.cfg) * CoreModel.MOX_BETA, betaSum(m.cfg), 1e-9);
        // setting it again does not lower it twice
        m.setMox(1.0);
        assertEquals(betaSum(u.cfg) * CoreModel.MOX_BETA, betaSum(m.cfg), 1e-9);
        m.setMox(0.0);
        assertEquals(betaSum(u.cfg), betaSum(m.cfg), 1e-12);
    }

    @Test
    void aRodStepIsWorthMoreDollarsWithMox() {
        CoreModel u = core(0.0), m = core(1.0);
        double du = u.reactivityWithRods(0.4, 0) - u.reactivityWithRods(0.5, 0);
        double dm = m.reactivityWithRods(0.4, 0) - m.reactivityWithRods(0.5, 0);
        assertTrue(du > 0 && dm > 0, "pulling rods adds reactivity: " + du + " " + dm);
        // physically 20 percent weaker rods, but every pcm is 1/0.6 dollars: about 1.33 times the dollars per step
        assertEquals((1.0 - CoreModel.MOX_ROD_LOSS) / CoreModel.MOX_BETA, dm / du, 0.02);
    }

    @Test
    void theSameStepGivesAShorterPeriodWithMox() {
        CoreModel u = core(0.0), m = core(1.0);
        double gen = 2e-5;
        double base = 0.5;
        double ru = u.reactivityWithRods(base - 0.02, 0) - u.reactivityWithRods(base, 0);
        double rm = m.reactivityWithRods(base - 0.02, 0) - m.reactivityWithRods(base, 0);
        double tu = ReactorPhysics.periodSeconds(ru, u.cfg.beta, u.cfg.lambda, gen);
        double tm = ReactorPhysics.periodSeconds(rm, m.cfg.beta, m.cfg.lambda, gen);
        assertTrue(tm < tu, "MOX period " + tm + " s should be shorter than uranium " + tu + " s");
    }
}
