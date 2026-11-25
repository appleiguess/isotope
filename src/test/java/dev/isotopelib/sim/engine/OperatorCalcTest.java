package dev.isotopelib.sim.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OperatorCalcTest {
    private final PlantConfig cfg = PlantConfig.standard();

    @Test
    void interpolatesTheRodTable() {
        float[] t = {2f, 1f, 0f, -1f, -2f};
        assertEquals(2.0, OperatorCalc.reactivityAtRods(t, 0, 5, 0.0), 1e-9);
        assertEquals(0.0, OperatorCalc.reactivityAtRods(t, 0, 5, 0.5), 1e-9);
        assertEquals(-1.5, OperatorCalc.reactivityAtRods(t, 0, 5, 0.875), 1e-9);
        assertEquals(-2.0, OperatorCalc.reactivityAtRods(t, 0, 5, 3.0), 1e-9);
    }

    @Test
    void periodFollowsTheInhourEquation() {
        double genTime = 2e-5;
        double t = OperatorCalc.period(0.1, cfg.beta, cfg.lambda, genTime);
        assertTrue(t > 40 && t < 200, "10 cents give a period of about a minute: " + t);
        assertTrue(Double.isInfinite(OperatorCalc.period(0.0, cfg.beta, cfg.lambda, genTime)));
        assertEquals(0.0, OperatorCalc.period(1.0, cfg.beta, cfg.lambda, genTime), 1e-12);
    }

    @Test
    void inverseInhourGivesThePeriodBack() {
        double genTime = 2e-5;
        double rho = OperatorCalc.maxReactivityForPeriod(10.0, cfg.beta, cfg.lambda, genTime);
        assertTrue(rho > 0.2 && rho < 0.8, "a 10 s period needs a few tens of cents: " + rho);
        assertEquals(10.0, OperatorCalc.period(rho, cfg.beta, cfg.lambda, genTime), 1e-3);
    }

    @Test
    void restartIsImpossibleAtTheXenonPeak() {
        // rods out give +0.5 $, xenon grows by 2 $ after the trip: no restart; once it decays again, restart works
        assertTrue(OperatorCalc.restartMargin(0.5, -1.0, -3.0) < 0);
        assertTrue(OperatorCalc.restartMargin(0.5, -1.0, -0.8) > 0);
    }

    @Test
    void decayHeatFallsAfterShutdown() {
        double year = 365 * 86400.0;
        double first = OperatorCalc.decayHeat(1.0 / 3600.0, year);
        double hour = OperatorCalc.decayHeat(1.0, year);
        double day = OperatorCalc.decayHeat(24.0, year);
        assertTrue(first > 0.04 && first < 0.08, "a few percent right after the trip: " + first);
        assertTrue(hour < first && day < hour);
    }

    @Test
    void steamAndTurbines() {
        double steam = OperatorCalc.steamAt(0.5, 14400.0, 2.0);
        assertEquals(14400.0, steam, 1e-9);
        assertEquals(6, OperatorCalc.turbinesFor(steam, 2400.0));
        assertEquals(1, OperatorCalc.turbinesFor(1.0, 2400.0));
        assertEquals(0, OperatorCalc.turbinesFor(0.0, 2400.0));
    }
}
