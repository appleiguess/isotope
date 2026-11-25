package dev.isotopelib.sim.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The physics of the desk pages: inhour equation, real units, heat balance, Way-Wigner, the Bateman solution of xenon. */
class ReactorPhysicsTest {
    private static final PlantConfig CFG = PlantConfig.standard();

    private static void near(double expected, double actual, double rel, String what) {
        assertTrue(Math.abs(expected - actual) <= Math.abs(expected) * rel, what + ": expected " + expected + " but was " + actual);
    }

    // ---------------------------------------------------------------- kinetics
    @Test
    void inhourRootSolvesTheEquation() {
        for (double rho : new double[]{-20, -3, -0.5, -0.05, 0.01, 0.1, 0.5, 0.9}) {
            double w = ReactorPhysics.inhourOmega(rho, CFG.beta, CFG.lambda, CFG.generationTime);
            assertEquals(rho, ReactorPhysics.inhourRho(w, CFG.beta, CFG.lambda, CFG.generationTime), 1e-9, "rho " + rho);
        }
    }

    @Test
    void smallReactivityGivesTheMeanDelayedLifetime() {
        // for rho -> 0: T = sum(f_i / lambda_i) / rho
        double b = ReactorPhysics.sum(CFG.beta), tau = 0;
        for (int i = 0; i < 6; i++)
            tau += CFG.beta[i] / b / CFG.lambda[i];
        double rho = 0.001;
        near(tau / rho, ReactorPhysics.periodSeconds(rho, CFG.beta, CFG.lambda, 0.0), 0.01, "period at 0.001 $");
    }

    @Test
    void negativePeriodIsBoundedByTheLongestLivedGroup() {
        double t = ReactorPhysics.periodSeconds(-30, CFG.beta, CFG.lambda, CFG.generationTime);
        near(-1.0 / CFG.lambda[0], t, 0.01, "period after a scram");
    }

    @Test
    void promptCriticalHasNoDelayedRoot() {
        assertTrue(Double.isNaN(ReactorPhysics.inhourOmega(1.2, CFG.beta, CFG.lambda, 0.0)));
        // with a real generation time the period becomes very short: about Lambda / (rho - beta)
        double t = ReactorPhysics.periodSeconds(1.5, CFG.beta, CFG.lambda, CFG.generationTime);
        assertTrue(t > 0 && t < 0.02, "prompt period " + t);
    }

    @Test
    void theEngineGrowsWithTheInhourPeriod() {
        // a hot zero power core without feedback: the power must grow exactly with the stable period of the inhour equation
        CoreModel core = new CoreModel(CFG);
        core.startAtPower(1e-6, 155.0);
        core.setBonusReactivity(0.1);
        double p1 = 0;
        for (int i = 0; i < 14000; i++) {
            core.step(0.05, 1.0, 540.0, 155.0, 1.0, 1.0);
            if (i == 7999)
                p1 = core.power();
        }
        double measured = Math.log(core.power() / p1) / 300.0;
        double expected = 1.0 / ReactorPhysics.periodSeconds(core.reactivityDollars(), CFG.beta, CFG.lambda, CFG.generationTime);
        near(expected, measured, 0.03, "growth rate");
    }

    @Test
    void kEffAndStartupRate() {
        assertEquals(1.0, ReactorPhysics.kEff(0, 0.0065), 1e-12);
        assertEquals(1.0 / (1.0 - 0.0065), ReactorPhysics.kEff(1.0, 0.0065), 1e-12);
        assertEquals(1.0, ReactorPhysics.startupRate(ReactorPhysics.SUR_CONSTANT), 1e-12);
        assertEquals(10.0, ReactorPhysics.multiplication(0.9), 1e-9);
    }

    @Test
    void criticalPositionAndShutdownMarginOfARatedCore() {
        CoreModel core = new CoreModel(CFG);
        core.startAtPower(1.0, 155.0);
        assertEquals(core.rodRegulating(), core.criticalRegulatingPosition(), 0.01);
        assertTrue(core.reactivityWithRods(1, 1) < -5.0, "shutdown margin " + core.reactivityWithRods(1, 1));
        double beta = ReactorPhysics.sum(CFG.beta);
        double doppler = core.dopplerCoefficient() * beta * 1e5;
        assertTrue(doppler < -1.0 && doppler > -6.0, "Doppler pcm/K " + doppler);
        assertTrue(core.moderatorCoefficient() < 0 && core.voidCoefficient() < 0);
    }

    // ---------------------------------------------------------------- real units
    @Test
    void neutronNumbersOfAFourLoopPwr() {
        near(2.82e20, ReactorPhysics.u235Density(4.0, 10.4, 0.30), 0.02, "U-235 atoms per cm3");
        near(506.0, ReactorPhysics.maxwellianSigma(585.1, 0.976, 293.6), 0.01, "Maxwellian sigma at room temperature");
        near(9.36e19, ReactorPhysics.fissionRate(3000e6), 0.01, "fissions per second at 3000 MW");
        near(2.48e5, ReactorPhysics.meanNeutronSpeed(293.6), 0.01, "mean speed at room temperature");
    }

    @Test
    void analysisOfARunningPlant() {
        PlantSim sim = new PlantSim(PlantConfig.standard());
        sim.startAtPower(1.0);
        for (int i = 0; i < 200; i++)
            sim.step(0.5);
        ReactorPhysics.History h = new ReactorPhysics.History();
        h.step(10, 1.0, 60);
        PhysicsReadout r = ReactorPhysics.analyze(sim, sim.snapshot(), h);
        assertTrue(r.flux() > 1e13 && r.flux() < 1e14, "flux " + r.flux());
        assertTrue(r.fissionRate() > 5e19 && r.fissionRate() < 1.5e20, "fission rate " + r.fissionRate());
        assertTrue(r.qLinAvg() > 12 && r.qLinAvg() < 20, "q' " + r.qLinAvg());
        assertTrue(r.carnot() > 0.35 && r.carnot() < 0.5, "Carnot " + r.carnot());
        assertTrue(r.subcooling() > 0, "subcooling " + r.subcooling());
        assertTrue(r.wayWigner() < 0, "no Way-Wigner value while running");
        assertEquals(ReactorPhysics.CURVE_POINTS, r.xenonCurve().length);
        assertTrue(r.xenonPeakHours() > 4 && r.xenonPeakHours() < 13, "xenon peak " + r.xenonPeakHours());
        assertTrue(r.xenonPeakWorth() < -3.5, "peak worth " + r.xenonPeakWorth());
        near(r.thermalMW(), r.qCalorimetric(), 0.25, "heat balance");
    }

    // ---------------------------------------------------------------- heat
    @Test
    void wayWignerOneHourAfterAYear() {
        double w = ReactorPhysics.wayWigner(3600, 3.15e7);
        assertTrue(w > 0.008 && w < 0.013, "decay heat after an hour " + w);
        assertTrue(ReactorPhysics.wayWigner(10, 3.15e7) > ReactorPhysics.wayWigner(100, 3.15e7));
    }

    @Test
    void waterSpecificHeatRisesTowardsSaturation() {
        assertEquals(5.42, ReactorPhysics.waterCp(300), 1e-9);
        assertTrue(ReactorPhysics.waterCp(320) > ReactorPhysics.waterCp(290));
        near(427, ReactorPhysics.pelletCentreRise(16100, 3.0), 0.01, "pellet centre rise");
    }

    // ---------------------------------------------------------------- xenon
    @Test
    void xenonPeakFromFullPowerEquilibrium() {
        double lamI = Math.log(2) / CFG.iodineHalfLifeHours, lamX = Math.log(2) / CFG.xenonHalfLifeHours;
        double b = CFG.xenonBurnRatio * lamX, a = lamX + b, g = CFG.xenonDirectShare;
        assertEquals(1.0, ReactorPhysics.xenonEquilibrium(1.0, CFG.xenonBurnRatio), 1e-12);
        assertEquals(1.0, ReactorPhysics.xenonAfterTrip(1, 1, 0, lamI, lamX, a, g), 1e-12);
        double t = ReactorPhysics.xenonPeakHours(1, 1, lamI, lamX, a, g);
        double x = ReactorPhysics.xenonAfterTrip(1, 1, t, lamI, lamX, a, g);
        assertTrue(x > 1.3 && x < 3.0, "peak " + x);
        double slope = (ReactorPhysics.xenonAfterTrip(1, 1, t + 1e-4, lamI, lamX, a, g) - ReactorPhysics.xenonAfterTrip(1, 1, t - 1e-4, lamI, lamX, a, g)) / 2e-4;
        assertEquals(0.0, slope, 1e-6);
        // a core without iodine has no peak
        assertEquals(-1.0, ReactorPhysics.xenonPeakHours(0, 1, lamI, lamX, a, g));
    }

    @Test
    void analyticXenonMatchesTheEngineAfterATrip() {
        CoreModel core = new CoreModel(CFG);
        core.startAtPower(1.0, 155.0);
        double i0 = core.meanIodine(), x0 = core.meanXenon();
        core.setScram(true);
        // six game minutes at a time compression of 60 are six real hours
        for (int i = 0; i < 7200; i++)
            core.step(0.05, 1.0, 548.0, 155.0, 1.0, 1.0);
        double lamI = Math.log(2) / CFG.iodineHalfLifeHours, lamX = Math.log(2) / CFG.xenonHalfLifeHours;
        double b = CFG.xenonBurnRatio * lamX;
        double predicted = ReactorPhysics.xenonAfterTrip(i0, x0, 6.0, lamI, lamX, lamX + b, CFG.xenonDirectShare);
        near(predicted, core.meanXenon(), 0.04, "xenon six hours after the trip");
    }

    @Test
    void historyCountsTheTimeSinceShutdown() {
        ReactorPhysics.History h = new ReactorPhysics.History();
        h.step(100, 1.0, 60);
        assertEquals(6000, h.operatingSeconds, 1e-9);
        h.step(1, 0.0, 60);
        h.step(1, 0.0, 60);
        assertTrue(!h.running && h.sinceShutdown == 120, "since shutdown " + h.sinceShutdown);
    }
}
