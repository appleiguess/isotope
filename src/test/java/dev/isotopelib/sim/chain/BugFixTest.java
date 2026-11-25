package dev.isotopelib.sim.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.isotopelib.radiation.RadiationMath;
import dev.isotopelib.sim.engine.CoreModel;
import dev.isotopelib.sim.engine.PlantConfig;
import dev.isotopelib.sim.engine.PlantSim;

class BugFixTest {
    @Test
    void turbineDoesNotFlickerAroundZero() {
        // barely any steam: the level hovers between 0 and 1, and every crossing of zero costs flicker score on the whole network
        int lvl = 0, changes = 0;
        long last = -1000;
        for (int t = 0; t < 20 * 120; t += 5) {
            int want = (t / 5) % 2;
            if (ChainMath.turbineLevelChange(lvl, want, 32, t - last)) {
                lvl = want;
                last = t;
                changes++;
            }
        }
        assertTrue(changes <= 24, "zero crossings in 2 min: " + changes);
    }

    @Test
    void turbineDoesNotFlickerOnPulsedSteam() {
        // pump pulses: the steam per step jumps between 0 and twice the mean
        int lvl = 0, changes = 0;
        long last = -1000;
        double avg = 0, load = 0;
        for (int t = 0; t < 20 * 120; t += 5) {
            double rate = (t / 5) % 2 == 0 ? 4800 : 0;
            avg = ChainMath.turbineLag(avg, rate, 3.0, 0.25);
            double tgt = ChainMath.clamp(avg / 2400.0, 0, 1);
            load = tgt >= load ? ChainMath.turbineLag(load, tgt, 6.0, 0.25) : ChainMath.turbineCoast(load, 6.0, 0.25);
            int want = (int) Math.round(load * 32);
            if (ChainMath.turbineLevelChange(lvl, want, 32, t - last)) {
                lvl = want;
                last = t;
                changes++;
            }
        }
        // Create breaks a generator past a flicker score of 128; a smooth start is a handful of changes
        assertTrue(changes < 20, "level changes in 2 min: " + changes);
        assertTrue(lvl >= 28, "it still reaches full speed: " + lvl);
    }

    @Test
    void geigerFollowsTheDose() {
        double low = RadiationMath.geigerClicksPerSecond(3);
        double high = RadiationMath.geigerClicksPerSecond(555);    // 2 Sv/h
        assertTrue(high / low > 50, low + " vs " + high);
        assertEquals(0.0, RadiationMath.geigerClicksPerSecond(0.01), 1e-9);
    }

    @Test
    void aFullInventoryDoesNotRocket() {
        double one = RadiationMath.stackSelfShield(1), stack = RadiationMath.stackSelfShield(64);
        assertEquals(1.0, one, 1e-9);
        assertEquals(8.0, stack, 1e-9);
        // 36 full stacks of uranium: 288 items' worth, not 2304
        assertEquals(288.0, 36 * stack, 1e-9);
    }

    @Test
    void aColdPlantIsCold() {
        PlantConfig cfg = PlantConfig.standard();
        PlantSim m = new PlantSim(cfg);
        m.setMode(PlantSim.Mode.DRIVEN);
        m.core.setTemperaturesInstant(293.15, 293.15);
        m.core.setRodsInstant(1.0, 1.0);
        m.setDrivenTemperature(293.15);
        for (int i = 0; i < 4 * 60; i++) {
            m.setDriven(1.0, 1.0, 1.0, 1.0);
            m.step(0.25);
        }
        double hot = m.snapshot().hotLegK() - 273.15, cold = m.snapshot().coldLegK() - 273.15;
        assertTrue(hot < 40 && cold < 40, "a shut down cold plant stays cold: " + hot + " / " + cold);
    }

    @Test
    void moxXenonIsWorthLess() {
        CoreModel u = new CoreModel(PlantConfig.standard()), x = new CoreModel(PlantConfig.standard());
        u.startAtPower(1.0, 155.0);
        x.startAtPower(1.0, 155.0);
        x.setMox(1.0);
        u.step(0.1, 1.0, 548.0, 155.0, 1.0, 1.0);
        x.step(0.1, 1.0, 548.0, 155.0, 1.0, 1.0);
        double wu = Math.abs(u.xenonWorthDollars()), wx = Math.abs(x.xenonWorthDollars());
        assertTrue(wx < wu * 1.2, "MOX xenon in dollars no longer ~1.7x: " + wu + " vs " + wx);
    }
}
