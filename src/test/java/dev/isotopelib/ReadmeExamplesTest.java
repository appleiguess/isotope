package dev.isotopelib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.isotopelib.fallout.FalloutField;
import dev.isotopelib.nuclide.Nuclide;
import dev.isotopelib.nuclide.NuclideInventory;
import dev.isotopelib.nuclide.Nuclides;
import dev.isotopelib.sim.engine.PlantConfig;
import dev.isotopelib.sim.engine.PlantSim;
import dev.isotopelib.sim.engine.PlantSnapshot;

/** The examples in the README, so they keep compiling and keep being true. */
class ReadmeExamplesTest {
    private static final double YEAR = 365.25 * 86400;

    @Test
    void reactor() {
        PlantSim plant = new PlantSim(PlantConfig.standard());
        plant.setMode(PlantSim.Mode.DRIVEN);      // you supply cooling and pressure
        plant.startAtPower(1.0);                  // 100 %
        plant.setDriven(1.0, 1.0, 1.0, plant.cfg.ratedPressure);  // flow, heat sink, water level, pressure in bar
        plant.manualTrip();                       // SCRAM
        for (int tick = 0; tick < 1200; tick++)   // one minute
            plant.step(0.05);
        PlantSnapshot s = plant.snapshot();
        assertTrue(s.power() < 0.05, "power " + s.power());
        assertTrue(s.decayHeat() > 0.01, "decay heat " + s.decayHeat());
    }

    @Test
    void waste() {
        NuclideInventory fuel = new NuclideInventory()
                .addGrams(Nuclides.CS137, 2)
                .addGrams(Nuclides.SR90, 1)
                .addGrams(Nuclides.PU241, 5);
        fuel.decay(30 * YEAR);                    // one step, exact
        assertTrue(fuel.heatWatts() > 0.1 && fuel.activityBq() > 1e12);
        assertTrue(fuel.grams(Nuclides.AM241) > 3);  // grew in from the plutonium
    }

    @Test
    void ownNuclide() {
        Nuclide thorium = Nuclides.register(new Nuclide("Th-232", 232, 1.405e10 * YEAR, Nuclide.Mode.ALPHA, 4.08, 0.002, null));
        assertEquals(4070, thorium.activityBqPerGram(), 60);
    }

    @Test
    void fallout() {
        FalloutField field = new FalloutField();
        field.release(0, 0, 2500);
        java.util.Random random = new java.util.Random(1);
        for (int i = 0; i < 100; i++)
            field.step(random::nextDouble);
        assertTrue(field.at(0, 0) < 2500);
    }
}
