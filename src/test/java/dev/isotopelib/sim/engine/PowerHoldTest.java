package dev.isotopelib.sim.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** After a rod move the power settles by itself and stays there for half an hour of play, through the whole xenon transient. */
class PowerHoldTest {
    private static final double DT = 0.25;

    private static PlantSim plant(double start) {
        PlantConfig cfg = PlantConfig.standard();
        cfg.timeCompression = 60.0;
        cfg.rodSpeed = 0.005;
        cfg.thermalTau = 4.0;
        cfg.voidTau = 2.0;
        cfg.dopplerScale = 1.8;
        cfg.fuelTimeConstant = 3.0;
        PlantSim m = new PlantSim(cfg);
        m.setMode(PlantSim.Mode.DRIVEN);
        m.startAtPower(start);
        m.setProtectionEnabled(false);
        return m;
    }

    private static double[] run(PlantSim m, PowerHold hold, double rodDelta, double seconds) {
        double base = m.core.rodRegulating();
        double target = base + rodDelta, lo = 9, hi = -9, last = 0;
        double before = base;
        for (double t = 0; t < seconds; t += DT) {
            m.setRodTargets(target, 0.0);
            double now = m.core.rodRegulating();
            boolean moving = Math.abs(now - before) > 1e-6;
            before = now;
            if (hold != null)
                m.core.setBonusReactivity(hold.step(m.core.power(), m.core.omega(), moving, true, DT));
            m.setDriven(1.0, 1.0, 1.0, m.cfg.ratedPressure);
            m.step(DT);
            last = m.core.power();
            if (t > 300) {
                lo = Math.min(lo, last);
                hi = Math.max(hi, last);
            }
        }
        return new double[]{lo, hi, last};
    }

    @Test
    void lettingGoKeepsTheTrim() {
        // switching Argus off must not yank the shim out: that would be a reactivity step of up to 6 $
        PowerHold hold = new PowerHold();
        for (int i = 0; i < 200; i++)
            hold.step(0.5, 0.0, false, true, 0.25);
        for (int i = 0; i < 400; i++)
            hold.step(0.4, 0.0, false, true, 0.25);
        double trim = hold.trim();
        assertTrue(Math.abs(trim) > 0.01, "the hold acted: " + trim);
        hold.step(0.4, 0.0, false, false, 0.25);
        assertTrue(hold.trim() == trim, "trim kept: " + hold.trim());
        assertTrue(hold.reference() < 0, "no longer holding");
    }

    @Test
    void holdsThePowerThroughXenon() {
        PlantSim m = plant(0.6);
        PowerHold hold = new PowerHold();
        double[] r = run(m, hold, -0.03, 1800.0);
        double[] free = run(plant(0.6), null, -0.03, 1800.0);
        System.out.printf("hold: lo %.4f hi %.4f last %.4f ref %.4f trim %.3f | free: lo %.4f hi %.4f last %.4f%n",
                r[0], r[1], r[2], hold.reference(), hold.trim(), free[0], free[1], free[2]);
        assertTrue(hold.reference() > 0.02, "latched");
        assertTrue(Math.abs(r[0] / hold.reference() - 1.0) < 0.03 && Math.abs(r[1] / hold.reference() - 1.0) < 0.03,
                "stays within 3 percent after five minutes");
    }
}
