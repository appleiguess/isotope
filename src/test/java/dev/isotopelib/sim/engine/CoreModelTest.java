package dev.isotopelib.sim.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The core on its own: kinetics, feedback, shape, xenon, decay heat, departure from nucleate boiling, oxidation. */
class CoreModelTest {

    private static void run(CoreModel core, double seconds, double dt, double flow, double inlet, double p, double level, double boost) {
        for (double t = 0; t < seconds; t += dt)
            core.step(dt, flow, inlet, p, level, boost);
    }

    private static CoreModel rated(PlantConfig cfg) {
        CoreModel core = new CoreModel(cfg);
        core.startAtPower(1.0, 155.0);
        return core;
    }

    private static void assertNear(double expected, double actual, double rel, String what) {
        assertTrue(Math.abs(expected - actual) <= Math.abs(expected) * rel, what + ": expected " + expected + " but was " + actual);
    }

    @Test
    void steadyStateAtRatedPowerIsStable() {
        CoreModel core = rated(PlantConfig.standard());
        run(core, 400, 0.05, 1.0, 548.0, 155.0, 1.0, 1.0);
        assertTrue(core.power() > 0.85 && core.power() < 1.15, "power " + core.power());
        assertTrue(core.minDnbr() > 1.3, "DNB ratio at rated power " + core.minDnbr());
        assertTrue(core.maxCladTemp() < 800.0, "clad " + core.maxCladTemp());
        assertEquals(573.0, core.outletTemp(), 6.0);
        assertFalse(core.isPrompt());
    }

    @Test
    void heatBalanceHolds() {
        CoreModel core = rated(PlantConfig.standard());
        run(core, 300, 0.05, 1.0, 548.0, 155.0, 1.0, 1.0);
        // heat into the coolant is fission plus decay heat
        assertNear(core.thermalPower(), core.heatToCoolant(), 0.12, "heat balance");
    }

    @Test
    void sixDelayedGroupsGiveASlowRiseForSmallReactivity() {
        PlantConfig cfg = PlantConfig.standard();
        CoreModel core = rated(cfg);
        run(core, 100, 0.05, 1.0, 548.0, 155.0, 1.0, 1.0);
        double p0 = core.power();
        core.addBoron(-0.3 / Math.abs(cfg.boronWorthPerPpm));      // dilute: about +0.3 dollars
        double max = 0;
        for (int i = 0; i < 100; i++) {
            core.step(0.05, 1.0, 548.0, 155.0, 1.0, 1.0);
            max = Math.max(max, core.power());
            assertFalse(core.isPrompt());
        }
        assertTrue(max > 1.05 * p0 && max < 2.0 * p0, "slow rise: " + p0 + " to " + max);
        run(core, 600, 0.05, 1.0, 548.0, 155.0, 1.0, 1.0);
        assertTrue(core.power() < 1.6 * p0, "the feedback stops the rise: " + core.power());
    }

    @Test
    void promptCriticalOnlyAboveOneDollar() {
        PlantConfig cfg = PlantConfig.standard();
        CoreModel a = rated(cfg);
        a.ejectRod(0.8);
        a.step(0.05, 1.0, 548.0, 155.0, 1.0, 1.0);
        assertFalse(a.isPrompt(), "0.8 dollars is delayed critical");
        CoreModel b = rated(cfg);
        b.ejectRod(1.6);
        double peak = 0;
        boolean prompt = false;
        for (int i = 0; i < 60; i++) {
            b.step(0.05, 1.0, 548.0, 155.0, 1.0, 1.0);
            prompt |= b.isPrompt();
            peak = Math.max(peak, b.power());
        }
        assertTrue(prompt, "1.6 dollars is prompt critical");
        assertTrue(peak > 2.0, "an excursion: " + peak);
        assertTrue(peak <= cfg.maxPromptPower + 1e-9);
    }

    @Test
    void scramDropsThePowerAndTheDecayHeatTakesOver() {
        CoreModel core = rated(PlantConfig.standard());
        run(core, 50, 0.05, 1.0, 548.0, 155.0, 1.0, 1.0);
        core.setScram(true);
        run(core, 60, 0.05, 1.0, 548.0, 155.0, 1.0, 1.0);
        assertTrue(core.power() < 0.05, "power after the scram " + core.power());
        assertTrue(core.decayHeat() > 0.01 && core.decayHeat() < 0.07, "decay heat " + core.decayHeat());
        assertTrue(core.reactivityDollars() < -3.0, "shutdown reactivity " + core.reactivityDollars());
    }

    @Test
    void decayHeatMatchesTheAnchorsInRealTime() {
        PlantConfig cfg = PlantConfig.standard();
        cfg.timeCompression = 1.0;
        CoreModel core = rated(cfg);
        core.setScram(true);
        run(core, 600, 0.5, 1.0, 548.0, 155.0, 1.0, 1.0);
        assertTrue(core.decayHeat() > 0.015 && core.decayHeat() < 0.04, "10 minutes: " + core.decayHeat());
        double t = 600;
        double[][] anchors = {{3600, 0.015}, {86400, 0.004}, {604800, 0.002}};
        for (double[] a : anchors) {
            while (t < a[0]) {
                core.step(5.0, 0.05, 548.0, 155.0, 1.0, 1.0);
                t += 5.0;
            }
            assertNear(a[1], core.decayHeat(), 0.2, "decay heat at " + a[0] + " s");
        }
    }

    @Test
    void xenonPeaksHoursAfterAShutdown() {
        PlantConfig cfg = PlantConfig.standard();
        cfg.timeCompression = 1.0;
        CoreModel core = rated(cfg);
        core.setScram(true);
        double t = 0, peak = 0, peakT = 0;
        while (t < 3 * 86400) {
            core.step(10.0, 0.05, 548.0, 155.0, 1.0, 1.0);
            t += 10.0;
            if (core.meanXenon() > peak) {
                peak = core.meanXenon();
                peakT = t;
            }
        }
        double hours = peakT / 3600.0;
        assertTrue(hours > 7.5 && hours < 13.75, "peak after " + hours + " hours");
        assertTrue(peak > 1.5, "peak " + peak);
        assertTrue(core.meanXenon() < 0.15, "gone after three days " + core.meanXenon());
    }

    @Test
    void samariumStaysAfterAShutdown() {
        CoreModel core = rated(PlantConfig.standard());
        core.setScram(true);
        run(core, 600, 0.5, 1.0, 548.0, 155.0, 1.0, 1.0);
        assertTrue(core.samarium() > 0.95, "samarium " + core.samarium());
    }

    @Test
    void promethiumDecayRaisesTheSamariumAfterAShutdown() {
        PlantConfig cfg = PlantConfig.standard();
        cfg.timeCompression = 1.0;
        CoreModel core = rated(cfg);
        core.setScram(true);
        run(core, 20 * 86400, 60.0, 0.05, 548.0, 155.0, 1.0, 1.0);
        assertEquals(1.0 + cfg.promethiumHalfLifeHours / Math.log(2) / cfg.samariumTauHours, core.samarium(), 0.03, "samarium");
        assertTrue(core.promethium() < 0.01, "the promethium has decayed");
    }

    @Test
    void rodWorthIsSShapedAndMonotone() {
        CoreModel core = rated(PlantConfig.standard());
        double last = 1e9;
        for (double x = 0; x <= 1.0001; x += 0.05) {
            core.setRodTargets(x, 0.0);
            // jump the rods instantly by running the rod mover for long enough
            for (int i = 0; i < 40000; i++) {
                core.step(0.05, 1.0, 548.0, 155.0, 1.0, 1.0);
                if (Math.abs(core.rodRegulating() - x) < 1e-9)
                    break;
            }
            double w = core.rodWorthDollars();
            assertTrue(w <= last + 1e-6, "more rod in means more negative reactivity");
            last = w;
        }
        assertTrue(core.rodWorthDollars() < -9.0, "all in: " + core.rodWorthDollars());
    }

    @Test
    void borationLowersTheReactivityByTheBoronWorth() {
        PlantConfig cfg = PlantConfig.standard();
        CoreModel core = rated(cfg);
        core.step(0.05, 1.0, 548.0, 155.0, 1.0, 1.0);
        double before = core.boronWorthDollars();
        core.addBoron(100.0);
        core.step(0.05, 1.0, 548.0, 155.0, 1.0, 1.0);
        assertEquals(before + 100.0 * cfg.boronWorthPerPpm, core.boronWorthDollars(), 0.01);
    }

    @Test
    void rodsInTheTopPushThePowerToTheBottom() {
        CoreModel core = rated(PlantConfig.standard());
        run(core, 100, 0.05, 1.0, 548.0, 155.0, 1.0, 1.0);
        double ao = core.axialOffset();
        assertTrue(ao < -0.02, "regulating rods in the top give a bottom-peaked shape: " + ao);
        core.setRodTargets(0.0, 0.0);
        run(core, 3000, 0.1, 1.0, 548.0, 155.0, 1.0, 1.0);
        assertTrue(core.axialOffset() > ao, "withdrawing the rods moves the shape up: " + core.axialOffset());
    }

    @Test
    void lossOfFlowAtPowerGivesDepartureFromNucleateBoiling() {
        CoreModel core = rated(PlantConfig.standard());
        run(core, 50, 0.05, 1.0, 548.0, 155.0, 1.0, 1.0);
        double cladBefore = core.maxCladTemp();
        run(core, 12, 0.05, 0.3, 548.0, 155.0, 1.0, 1.0);
        assertTrue(core.minDnbr() < 1.3, "DNB ratio with 30 percent flow " + core.minDnbr());
        run(core, 10, 0.05, 0.1, 548.0, 155.0, 1.0, 1.0);
        assertTrue(core.maxCladTemp() > cladBefore + 20.0, "the cladding heats up in film boiling");
    }

    @Test
    void voidCoefficientHasTheRightSignInBothDesigns() {
        for (boolean graphite : new boolean[]{false, true}) {
            PlantConfig cfg = graphite ? PlantConfig.graphite() : PlantConfig.standard();
            cfg.kModerator = 0.0;
            CoreModel core = rated(cfg);
            run(core, 3, 0.05, 1.0, 548.0, 155.0, 1.0, 1.0);
            double r0 = core.reactivityDollars();
            // less pressure means boiling in the core: a big void. Look at the reactivity right after the pressure drop.
            double r1 = graphite ? -99.0 : 99.0;
            for (int i = 0; i < 20; i++) {
                core.step(0.05, 1.0, 548.0, 12.0, 1.0, 1.0);
                r1 = graphite ? Math.max(r1, core.reactivityDollars()) : Math.min(r1, core.reactivityDollars());
            }
            if (graphite)
                assertTrue(r1 > r0 + 0.3, "graphite: voids add reactivity " + r0 + " to " + r1);
            else
                assertTrue(r1 < r0 - 0.3, "light water: voids take reactivity away " + r0 + " to " + r1);
        }
    }

    @Test
    void anUncoveredHotCoreOxidisesMakesHydrogenAndMelts() {
        PlantConfig cfg = PlantConfig.standard();
        CoreModel core = rated(cfg);
        core.setScram(true);
        run(core, 60, 0.05, 1.0, 548.0, 155.0, 1.0, 1.0);
        // the water is gone, the heat has nowhere to go; the decay heat of the compressed accident time heats the core
        double h2 = 0, cladMax = 0;
        for (int i = 0; i < 40000; i++) {
            core.step(0.05, 0.0, 548.0, 20.0, 0.1, 60.0);
            h2 = core.hydrogenTotalKg();
            cladMax = Math.max(cladMax, core.maxCladTemp());
        }
        assertTrue(cladMax > cfg.cladRuptureK, "the cladding reaches the rupture temperature: " + cladMax);
        assertTrue(h2 > 5.0, "hydrogen was made: " + h2);
        assertTrue(h2 <= cfg.zirconiumHydrogenKg + 1e-6, "no more than the zirconium can give: " + h2);
        assertTrue(core.ruptureFraction() > 0.5, "cladding burst " + core.ruptureFraction());
        assertTrue(core.gapReleasedFraction() > 0.5);
    }

    @Test
    void aCoreThatIsCoveredAndCooledStaysIntactAfterAScram() {
        CoreModel core = rated(PlantConfig.standard());
        core.setScram(true);
        run(core, 300, 0.05, 0.3, 548.0, 155.0, 1.0, 1.0);
        assertTrue(core.maxCladTemp() < 700.0);
        assertEquals(0.0, core.hydrogenTotalKg(), 1e-9);
        assertEquals(0.0, core.meltFraction(), 1e-9);
    }

    @Test
    void burnupAccumulatesWithPowerAndFreesTheReactivity() {
        PlantConfig cfg = PlantConfig.standard();
        cfg.assemblyLifeSeconds = 1000.0;           // fast for the test
        CoreModel core = rated(cfg);
        core.step(0.05, 1.0, 548.0, 155.0, 1.0, 1.0);
        double fuel0 = core.fuelReactivityDollars();
        run(core, 500, 0.1, 1.0, 548.0, 155.0, 1.0, 1.0);
        assertTrue(core.meanBurnup() > 10.0 && core.meanBurnup() < 30.0, "burnup " + core.meanBurnup());
        assertTrue(core.fuelReactivityDollars() < fuel0, "the fuel excess falls with burnup");
        core.refuel(1.0);
        assertEquals(0.0, core.meanBurnup(), 1e-9);
    }

    @Test
    void saveAndLoadGiveTheSameCore() {
        PlantConfig cfg = PlantConfig.standard();
        CoreModel a = rated(cfg);
        run(a, 120, 0.05, 1.0, 548.0, 155.0, 1.0, 1.0);
        java.util.List<Double> list = new java.util.ArrayList<>();
        a.save(list);
        assertEquals(CoreModel.saveSize(), list.size());
        double[] data = new double[list.size()];
        for (int i = 0; i < data.length; i++)
            data[i] = list.get(i);
        CoreModel b = new CoreModel(cfg);
        assertEquals(data.length, b.load(data, 0));
        for (int i = 0; i < 400; i++) {
            a.step(0.05, 1.0, 548.0, 155.0, 1.0, 1.0);
            b.step(0.05, 1.0, 548.0, 155.0, 1.0, 1.0);
        }
        assertNear(a.power(), b.power(), 1e-6, "power");
        assertNear(a.maxFuelTemp(), b.maxFuelTemp(), 1e-6, "fuel temperature");
        assertNear(a.meanXenon(), b.meanXenon(), 1e-6, "xenon");
    }

    @Test
    void stepSizeDoesNotChangeTheAnswerMuch() {
        double fine = powerAfterRodPull(0.05), coarse = powerAfterRodPull(0.1);
        assertNear(fine, coarse, 0.1, "power after a rod pull");
    }

    private static double powerAfterRodPull(double dt) {
        CoreModel core = rated(PlantConfig.standard());
        core.setRodTargets(core.rodRegulating() - 0.03, 0.0);
        run(core, 150, dt, 1.0, 548.0, 155.0, 1.0, 1.0);
        return core.power();
    }

    /** The graphite tips of the old design add reactivity at the start of a scram, light water rods do not. */
    @Test
    void thePositiveScramOfTheGraphiteDesign() {
        double[] delta = new double[2];
        int k = 0;
        for (double bonus : new double[]{0.0, 1.4}) {
            PlantConfig cfg = PlantConfig.standard();
            cfg.positiveScramDollars = bonus;
            cfg.scramTime = 6.0;
            CoreModel core = rated(cfg);
            core.startAtPower(0.05, 155.0);
            run(core, 5, 0.05, 1.0, 548.0, 155.0, 1.0, 1.0);
            double r0 = core.reactivityDollars();
            core.setScram(true);
            run(core, 2, 0.05, 1.0, 548.0, 155.0, 1.0, 1.0);
            delta[k++] = core.reactivityDollars() - r0;
        }
        assertTrue(delta[1] > delta[0] + 0.8, "the graphite design loses less reactivity at the start of a scram: " + delta[0] + " against " + delta[1]);
    }
}
