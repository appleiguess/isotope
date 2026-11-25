package dev.isotopelib.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests of the pure Java reactor simulation. No Minecraft classes are needed. */
class ReactorSimTest {

    private static void assertNear(double expected, double actual, double relative, String what) {
        double tol = Math.abs(expected) * relative;
        assertTrue(Math.abs(expected - actual) <= tol, what + ": expected " + expected + " +-" + relative * 100 + "% but was " + actual);
    }

    /** Decay heat after a scram, in real time, against the four anchors: 6.5 percent, 1.5 percent at 1 h, 0.4 percent at 1 day, 0.2 percent at 1 week. */
    @Test
    void decayHeatMatchesTheAnchorsInRealTime() {
        SimConfig cfg = new SimConfig();
        cfg.timeCompression = 1.0;
        ReactorSim sim = new ReactorSim(cfg);
        sim.startAtRatedPower(1.0);
        sim.setScram(true);
        double t = 0;
        double[][] anchors = {{3600, 0.015}, {86400, 0.004}, {604800, 0.002}};
        // power of the delayed neutrons dies away in a few minutes, check the start after that
        while (t < 600) { sim.step(1.0); t += 1.0; }
        assertTrue(sim.decayHeatFraction() > 0.015 && sim.decayHeatFraction() < 0.04, "decay heat after 10 minutes: " + sim.decayHeatFraction());
        int next = 0;
        while (next < anchors.length) {
            sim.step(10.0);
            t += 10.0;
            if (t >= anchors[next][0]) {
                assertNear(anchors[next][1], sim.decayHeatFraction(), 0.20, "decay heat at " + anchors[next][0] + " s");
                next++;
            }
        }
    }

    /** With time compression the same curve runs faster by exactly that factor. */
    @Test
    void decayHeatScalesWithTimeCompression() {
        SimConfig cfg = new SimConfig();
        cfg.timeCompression = 120.0;
        ReactorSim sim = new ReactorSim(cfg);
        sim.startAtRatedPower(1.0);
        sim.setScram(true);
        double t = 0;
        while (t < 30.0) { sim.step(0.5); t += 0.5; }            // 30 game seconds are one real hour at 120
        assertNear(0.015, sim.decayHeatFraction(), 0.25, "decay heat after one compressed hour");
    }

    /** At the rated point the light water reactor settles and stays bounded. */
    @Test
    void lightWaterSteadyStateIsStable() {
        ReactorSim sim = new ReactorSim(SimConfig.lightWater());
        sim.startAtRatedPower(1.0);
        double maxFuel = 0, maxPower = 0, minPower = 10;
        for (int i = 0; i < 10000; i++) {
            sim.step(0.05);
            assertFalse(Double.isNaN(sim.power()) || Double.isNaN(sim.fuelTemp()), "NaN in the state");
            maxFuel = Math.max(maxFuel, sim.fuelTemp());
            if (i > 2000) { maxPower = Math.max(maxPower, sim.power()); minPower = Math.min(minPower, sim.power()); }
        }
        assertTrue(maxFuel < 1100, "fuel temperature stayed below clad failure: " + maxFuel);
        assertTrue(maxPower < 1.3 && minPower > 0.5, "power stayed bounded: " + minPower + " to " + maxPower);
        assertEquals(DamageStage.NORMAL, sim.damageStage());
    }

    /** A small step of reactivity makes a slow rise, limited by the delayed neutrons. */
    @Test
    void smallReactivityStepGivesASlowRise() {
        ReactorSim sim = new ReactorSim(SimConfig.lightWater());
        sim.startAtRatedPower(1.0);
        sim.setBonusReactivity(0.3);
        double p0 = sim.power();
        double max = 0;
        for (int i = 0; i < 100; i++) {                       // 5 game seconds
            sim.step(0.05);
            max = Math.max(max, sim.power());
            assertFalse(sim.isPrompt(), "not prompt critical at 0.3 dollars");
        }
        assertTrue(max < 2.0 * p0, "the power did not double in 5 s: " + max);
        assertTrue(max > 1.2 * p0, "but it did rise: " + max);
        // the temperature feedback has taken the extra power back after a while: the reactor settles instead of running away
        for (int i = 0; i < 4000; i++)
            sim.step(0.05);
        assertTrue(sim.power() < 1.6 * p0, "the light water reactor settles: " + sim.power());
    }

    /** One dollar and more is prompt critical: a fast excursion that is stopped by the cap or the feedback. */
    @Test
    void promptCriticalOnlyAboveOneDollar() {
        ReactorSim below = new ReactorSim(SimConfig.lightWater());
        below.startAtRatedPower(1.0);
        below.setBonusReactivity(0.9);
        below.step(0.05);
        assertFalse(below.isPrompt(), "0.9 dollars is not prompt");

        ReactorSim above = new ReactorSim(SimConfig.lightWater());
        above.startAtRatedPower(1.0);
        above.setBonusReactivity(1.3);
        above.step(0.05);
        assertTrue(above.isPrompt(), "1.3 dollars is prompt critical");
        double peak = 0;
        for (int i = 0; i < 40; i++) {
            above.step(0.05);
            peak = Math.max(peak, above.power());
        }
        assertTrue(peak > 2.0, "a fast excursion: " + peak);
        assertTrue(above.power() <= SimConfig.lightWater().maxPromptPower + 1e-9, "the cap holds");
    }

    /** After a drop of power the xenon peaks hours later. At a compression of 1 the peak comes in the expected window. */
    @Test
    void xenonPeaksHoursAfterAShutdown() {
        SimConfig cfg = new SimConfig();
        cfg.timeCompression = 1.0;
        ReactorSim sim = new ReactorSim(cfg);
        sim.startAtRatedPower(1.0);
        sim.setScram(true);
        double t = 0, peak = 0, peakTime = 0;
        while (t < 3 * 86400) {
            sim.step(10.0);
            t += 10.0;
            if (sim.xenon() > peak) { peak = sim.xenon(); peakTime = t; }
        }
        double hours = peakTime / 3600.0;
        assertTrue(hours > 8.25 / 1.0 * 0.95 && hours < 13.75, "xenon peak after " + hours + " hours");
        assertTrue(peak > 1.5, "the peak is well above the equilibrium: " + peak);
        assertTrue(sim.xenon() < 0.1, "xenon is gone after three days: " + sim.xenon());
    }

    /** The sign of the void feedback: negative for light water, positive for the graphite design. */
    @Test
    void voidCoefficientHasTheRightSign() {
        for (boolean graphite : new boolean[]{false, true}) {
            SimConfig cfg = graphite ? SimConfig.graphite() : SimConfig.lightWater();
            cfg.coolantTempPerKelvin = 0.0;                    // look at the void alone
            ReactorSim sim = new ReactorSim(cfg);
            sim.startAtRatedPower(0.5);
            double rho0 = sim.reactivity();
            sim.setTemperaturesInstant(sim.fuelTemp(), cfg.boilTemp + 50.0);   // half of the coolant is steam
            double rho1 = sim.reactivity();
            if (graphite)
                assertTrue(rho1 > rho0 + 0.5, "graphite: voids add reactivity: " + rho0 + " to " + rho1);
            else
                assertTrue(rho1 < rho0 - 0.5, "light water: voids take reactivity away: " + rho0 + " to " + rho1);
        }
    }

    /** After a loss of flow at medium power the light water reactor stays below its starting power, the graphite design runs away. */
    @Test
    void lossOfFlowIsSelfLimitingInLightWaterButNotInTheGraphiteDesign() {
        double lw = flowTripPeak(SimConfig.lightWater());
        double gr = flowTripPeak(SimConfig.graphite());
        assertTrue(lw < 0.65, "light water: the power only falls after the pumps trip, peak " + lw);
        assertTrue(gr > lw * 1.3, "graphite: the voids add power, peak " + gr + " against " + lw);
    }

    private static double flowTripPeak(SimConfig cfg) {
        ReactorSim sim = new ReactorSim(cfg);
        sim.startAtRatedPower(0.6);
        sim.setRodTarget(sim.rodRegulating());
        sim.setCoolantFlow(0.0);               // the pumps trip, the coolant heats up and starts to boil
        double peak = 0;
        for (int i = 0; i < 2400; i++) {
            sim.step(0.05);
            peak = Math.max(peak, sim.power());
        }
        return peak;
    }

    /** The step size does not change the answer much, so the server can catch up with a coarser step. */
    @Test
    void stepSizeDoesNotMatter() {
        double fine = runPowerUp(0.05), coarse = runPowerUp(0.25);
        assertNear(fine, coarse, 0.1, "power after a rod pull with two step sizes");
    }

    private static double runPowerUp(double dt) {
        ReactorSim sim = new ReactorSim(SimConfig.lightWater());
        sim.startAtRatedPower(0.5);
        sim.setRodTarget(sim.rodRegulating() - 0.02);
        for (double t = 0; t < 120; t += dt)
            sim.step(dt);
        return sim.power();
    }

    /** Saving and loading gives the same state. */
    @Test
    void saveAndLoadRoundTrip() {
        ReactorSim a = new ReactorSim(SimConfig.lightWater());
        a.startAtRatedPower(0.8);
        for (int i = 0; i < 500; i++)
            a.step(0.05);
        double[] data = a.save();

        ReactorSim b = new ReactorSim(SimConfig.lightWater());
        b.load(data);
        for (int i = 0; i < 200; i++) {
            a.step(0.05);
            b.step(0.05);
        }
        assertNear(a.power(), b.power(), 1e-6, "power after a reload");
        assertNear(a.fuelTemp(), b.fuelTemp(), 1e-6, "fuel temperature after a reload");
        assertNear(a.xenon(), b.xenon(), 1e-6, "xenon after a reload");
    }

    /** Broken or unknown saved data leaves a safe, shut down reactor. */
    @Test
    void badDataIsIgnored() {
        ReactorSim sim = new ReactorSim(SimConfig.lightWater());
        sim.load(new double[]{99, 1, 2});
        assertTrue(sim.power() < 1e-3);
        assertEquals(DamageStage.NORMAL, sim.damageStage());
    }

    /** Without cooling the core of a scrammed reactor still overheats from the decay heat, the lesson of Three Mile Island and Fukushima. */
    @Test
    void decayHeatCanMeltAScrammedCoreWithoutCooling() {
        SimConfig cfg = new SimConfig();
        ReactorSim sim = new ReactorSim(cfg);
        sim.startAtRatedPower(1.0);
        sim.setScram(true);
        sim.setCoolantFlow(0.0);
        sim.setHeatSink(0.0);
        boolean reachedDamage = false;
        for (int i = 0; i < 200000 && !reachedDamage; i++) {
            sim.step(0.05);
            reachedDamage = sim.damageStage() != DamageStage.NORMAL;
        }
        assertTrue(reachedDamage, "the core must overheat without any heat removal");
        // and with the heat sink back and flow, the same scrammed core stays cold
        ReactorSim safe = new ReactorSim(new SimConfig());
        safe.startAtRatedPower(1.0);
        safe.setScram(true);
        for (int i = 0; i < 100000; i++)
            safe.step(0.05);
        assertEquals(DamageStage.NORMAL, safe.damageStage());
    }

    /** The rod reserve gets smaller when the rods are pulled out. */
    @Test
    void rodReserveFallsWhenRodsAreWithdrawn() {
        ReactorSim sim = new ReactorSim(SimConfig.lightWater());
        sim.setRodsInstant(1.0, 0.0);
        double full = sim.rodReserveDollars();
        sim.setRodsInstant(0.2, 0.0);
        assertTrue(sim.rodReserveDollars() < full);
    }

    /** A cold core with all rods in has a good shutdown margin, and a cold start reaches criticality at a reasonable rod position. */
    @Test
    void coldShutdownMarginAndColdCriticality() {
        ReactorSim sim = new ReactorSim(SimConfig.lightWater());
        sim.setRodsInstant(1.0, 1.0);
        assertTrue(sim.reactivity() < -3.0, "shutdown margin of a cold core: " + sim.reactivity());
        sim.setRodsInstant(1.0, 0.0);
        double critical = -1;
        for (double x = 1.0; x >= 0.0; x -= 0.01) {
            sim.setRodsInstant(x, 0.0);
            if (sim.reactivity() > 0) {
                critical = x;
                break;
            }
        }
        assertTrue(critical > 0.4 && critical < 0.99, "the cold core becomes critical at a rod position of " + critical);
    }

    /** A careful operator pulls a little, waits until the power has answered, and stops when the power is where he wants it. */
    @Test
    void carefulStartupStaysBelowPromptCritical() {
        SimConfig cfg = SimConfig.lightWater();
        cfg.rodSpeed = 0.015;
        ReactorSim sim = new ReactorSim(cfg);
        sim.setRodsInstant(1.0, 1.0);
        sim.setShutdownTarget(0.0);
        sim.setCoolantFlow(1.0);
        double target = 1.0;
        boolean prompt = false;
        double peak = 0;
        double powerBefore = sim.power();
        for (int i = 0; i < 60000; i++) {          // 3000 game seconds
            if (i % 200 == 0) {                    // every ten seconds the operator looks at the power
                double now = sim.power();
                boolean rising = now > 1.25 * powerBefore;
                if (sim.rodShutdown() < 0.01 && now < 0.25 && !rising)
                    target = Math.max(0.0, target - 0.004);
                else if (now > 0.35)
                    target = Math.min(1.0, target + 0.004);
                sim.setRodTarget(target);
                powerBefore = now;
            }
            sim.step(0.05);
            prompt |= sim.isPrompt();
            peak = Math.max(peak, sim.power());
        }
        assertFalse(prompt, "no prompt critical excursion in a careful startup");
        assertTrue(peak < 1.5, "the startup peak stays moderate: " + peak);
        assertTrue(sim.power() > 0.05, "the reactor is running: " + sim.power());
    }
}
