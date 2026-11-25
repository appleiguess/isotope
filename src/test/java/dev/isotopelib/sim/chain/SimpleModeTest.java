package dev.isotopelib.sim.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.isotopelib.sim.engine.SimpleCore;

/** Simple physics (Config.PhysicsMode.SIMPLE): the power follows the rods, the heat still has to go somewhere. */
class SimpleModeTest {
    static final double DT = 0.25, HEAT = 3.0, COOL = HEAT / 580.0, AMBIENT = 20.0;

    /** The Creator's core: 15 channels, 4000 mB of 4 % fuel. */
    static double target(int insertion) {
        return ChainMath.simplePowerTarget(insertion, 4.0, 4000, 15, 0.0);
    }

    @Test
    void theRodsSetThePower() {
        assertEquals(0.0, target(100), 1e-9);
        assertEquals(0.7, target(30), 1e-9);
        assertEquals(1.0, target(0), 1e-9);
        assertEquals(0.0, ChainMath.simplePowerTarget(0, 4.0, 0, 15, 0.0), 1e-9, "no fuel, no power");
        assertTrue(ChainMath.simplePowerTarget(0, 0.711, 4000, 15, 0.0) < 0.2, "natural uranium barely runs");
        assertTrue(ChainMath.simplePowerTarget(0, 4.0, 4000, 15, 1.0) <= 1.2);
    }

    @Test
    void itStartsFromTheSourceAndSettlesWithoutAShortPeriod() {
        SimpleCore core = new SimpleCore();
        double t = 290, minPeriod = 1e9, reached = -1;
        for (int i = 0; i < 4 * 300; i++) {
            double before = core.power();
            core.step(target(30), false, true, 8.0, DT);
            double p = core.power();
            t = ChainMath.simpleTemperatureStep(t, p + 0.05, 1.0, AMBIENT, HEAT, COOL, DT);
            double w = Math.log(p / before) / DT;
            if (p > 0.01 && w > 1e-4)
                minPeriod = Math.min(minPeriod, 1.0 / w);
            if (reached < 0 && p > 0.65)
                reached = i * DT;
        }
        assertTrue(core.power() > 0.68 && core.power() < 0.85, "five minutes after pulling the slider to 30 %, a fresh core has no xenon yet: " + core.power());
        assertTrue(reached > 60 && reached < 240, "a start from the source level takes minutes: " + reached);
        assertTrue(minPeriod > 10.0, "no short period at power: " + minPeriod);
        assertTrue(t > 300 && t < 1000, "cooled, it stays below the scram temperature: " + t);

        core.step(0.0, true, true, 8.0, DT);
        for (int i = 0; i < 4 * 3; i++)
            core.step(0.0, true, true, 8.0, DT);
        assertTrue(core.power() < 0.12, "the prompt drop " + core.power());
        double a = core.power();
        for (int i = 0; i < 4 * 60; i++)
            core.step(0.0, true, true, 8.0, DT);
        double period = 60.0 / Math.log(core.power() / a);
        assertTrue(period < -25 && period > -90, "then the delayed neutrons die away: " + period);
    }

    @Test
    void xenonFollowsThePower() {
        SimpleCore core = new SimpleCore();
        core.startAt(1.0);
        assertEquals(1.0, core.xenon(), 1e-9);
        for (int i = 0; i < 4 * 60; i++)
            core.step(0.0, true, true, 8.0, DT);
        double peak = 0;
        for (int i = 0; i < 4 * 1200; i++) {
            core.step(0.0, true, true, 8.0, DT);
            peak = Math.max(peak, core.xenon());
        }
        assertTrue(peak > 1.5, "xenon peaks after a trip: " + peak);
        core.startAt(1.0);
        for (int i = 0; i < 4 * 360; i++)
            core.step(0.0, true, true, 8.0, DT);
        double atPower = -1, held = 0;
        for (int i = 0; i < 4 * 1200; i++) {
            core.step(1.0, false, true, 8.0, DT);
            if (atPower < 0 && core.power() > 0.5)
                atPower = i * DT;
            if (atPower >= 0 && i * DT < atPower + 60)
                held = Math.max(held, core.power());
        }
        assertTrue(held < 0.95, "restarting into the xenon peak gives less power: " + held);
        assertTrue(core.power() > 0.97, "until the xenon burns out: " + core.power());
    }

    @Test
    void withoutCoolingItStillMeltsDown() {
        double t = 600;
        for (int i = 0; i < 4 * 600; i++)
            t = ChainMath.simpleTemperatureStep(t, 1.0, 0.0, AMBIENT, HEAT, COOL, DT);
        assertTrue(t > 1800, "ten minutes at full power with no heat sink: " + t);
    }
}
