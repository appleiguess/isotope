package dev.isotopelib.sim.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Six-group point kinetics with a neutron source, checked against the inhour equation and textbook limits. */
class PointKineticsTest {
    private static final PlantConfig CFG = new PlantConfig();

    private static PointKinetics at(double p) {
        PointKinetics k = new PointKinetics(CFG.beta, CFG.lambda, CFG.generationTime);
        k.setEquilibrium(p);
        return k;
    }

    @Test
    void theStablePeriodMatchesTheInhourEquation() {
        for (double rho : new double[]{0.1, 0.3, 0.5}) {
            PointKinetics k = at(1e-12);
            for (int i = 0; i < 4 * 200; i++)
                k.step(rho, 0, 0.25, 1e30);
            double inhour = ReactorPhysics.periodSeconds(rho, CFG.beta, CFG.lambda, CFG.generationTime);
            assertEquals(inhour, k.periodSeconds(), inhour * 0.01, rho + " dollars");
        }
    }

    @Test
    void thePromptJumpIsOneOverOneMinusRho() {
        PointKinetics k = at(1e-3);
        k.step(0.5, 0, 0.02, 1e9);
        assertEquals(2.0, k.power() / 1e-3, 0.08);
        PointKinetics d = at(1.0);
        d.step(-8.0, 0, 0.02, 1e9);
        assertEquals(1.0 / 9.0, d.power(), 0.01, "a scram drops the power to beta over beta minus rho");
    }

    @Test
    void aSourceGivesSubcriticalMultiplication() {
        for (double rho : new double[]{-10.0, -1.0, -0.1}) {
            PointKinetics k = at(1e-9);
            for (int i = 0; i < 4 * 3000; i++)
                k.step(rho, 1e-7, 0.25, 1e9);
            assertEquals(1e-7 / -rho, k.power(), 1e-7 / -rho * 0.01, rho + " dollars");
            assertTrue(Math.abs(k.omega()) < 1e-4, "steady");
        }
    }

    @Test
    void theLongestGroupSetsTheShutdownPeriod() {
        PointKinetics k = at(1.0);
        for (int i = 0; i < 4 * 300; i++)
            k.step(-8.0, 0, 0.25, 1e9);
        assertEquals(-1.0 / CFG.lambda[0], k.periodSeconds(), 8.0, "about -80 s");
    }

    @Test
    void stepSizeDoesNotMatter() {
        PointKinetics a = at(1e-3), b = at(1e-3);
        for (int i = 0; i < 2000; i++)
            a.step(0.4, 0, 0.05, 1e9);
        for (int i = 0; i < 400; i++)
            b.step(0.4, 0, 0.25, 1e9);
        assertEquals(1.0, a.power() / b.power(), 0.01);
    }
}
