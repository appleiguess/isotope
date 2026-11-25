package dev.isotopelib.sim.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The whole plant: normal operation, the accidents of the real world, persistence and the driven mode. */
class PlantSimTest {

    private static PlantSim running() {
        PlantSim sim = new PlantSim(PlantConfig.standard());
        sim.startAtPower(1.0);
        sim.step(30);
        return sim;
    }

    private static void run(PlantSim sim, double seconds) {
        for (double t = 0; t < seconds; t += 0.5)
            sim.step(0.5);
    }

    @Test
    void thePlantRunsAtPowerWithoutATrip() {
        PlantSim sim = running();
        run(sim, 600);
        PlantSnapshot s = sim.snapshot();
        assertTrue(s.power() > 0.85 && s.power() < 1.1, "power " + s.power());
        assertTrue(s.pressureBar() > 145 && s.pressureBar() < 165, "pressure " + s.pressureBar());
        assertTrue(s.sgLevel() > 0.35 && s.sgLevel() < 0.65, "SG level " + s.sgLevel());
        assertTrue(s.electricFraction() > 0.8, "electric " + s.electricFraction());
        assertFalse(s.tripped());
        assertEquals(PlantSnapshot.DamageState.NORMAL, s.damage());
        assertTrue(s.minDnbr() > 1.3);
        assertEquals(0, s.inesLevel());
    }

    @Test
    void aLossOfOffsitePowerIsHandledBySafetySystems() {
        PlantSim sim = running();
        sim.inject(FailureEvent.LOSS_OF_OFFSITE_POWER, 0);
        run(sim, 400);
        PlantSnapshot s = sim.snapshot();
        assertTrue(s.tripped());
        assertTrue(s.acAvailable(), "the diesels run");
        assertTrue(s.maxCladK() < 900.0, "the core stays cool: " + s.maxCladK());
        assertEquals(0.0, s.hydrogenKgTotal(), 1e-9);
        assertTrue(s.flow() < 0.2, "the pumps have coasted down and natural circulation remains: " + s.flow());
    }

    @Test
    void aStationBlackoutLeadsToCoreDamageAndARelease() {
        PlantSim sim = running();
        sim.inject(FailureEvent.STATION_BLACKOUT, 0);
        run(sim, 2000);
        PlantSnapshot s = sim.snapshot();
        assertFalse(s.acAvailable());
        assertTrue(s.claddingDamage() > 0.1, "core damage " + s.claddingDamage());
        assertTrue(s.hydrogenKgTotal() > 20.0, "hydrogen " + s.hydrogenKgTotal());
        assertTrue(s.inesLevel() >= 5, "INES " + s.inesLevel());
        assertNotEquals(PlantSnapshot.DamageState.NORMAL, s.damage());
    }

    @Test
    void aStickyReliefValveLosesWaterButTheEmergencySystemsSaveTheCore() {
        PlantSim sim = running();
        sim.inject(FailureEvent.PORV_STUCK_OPEN, 0);
        run(sim, 120);
        PlantSnapshot s = sim.snapshot();
        assertTrue(s.porvOpen() && s.porvStuck());
        assertTrue(s.primaryMass() < 0.97, "water is being lost: " + s.primaryMass());
        run(sim, 700);
        s = sim.snapshot();
        assertTrue(s.hpi(), "the high pressure injection has started");
        assertTrue(s.meltFraction() == 0.0, "no melt: the lesson of Three Mile Island is that the operator must not stop the injection");
    }

    @Test
    void closingTheBlockValveStopsTheLossOfWater() {
        PlantSim sim = running();
        sim.inject(FailureEvent.PORV_STUCK_OPEN, 0);
        run(sim, 60);
        double m1 = sim.snapshot().primaryMass();
        sim.primary.unstickPorv();
        sim.primary.closeBlockValve();
        run(sim, 120);
        double m2 = sim.snapshot().primaryMass();
        assertTrue(m2 >= m1 - 0.01, "the loss of water has stopped: " + m1 + " to " + m2);
    }

    @Test
    void aLargeBreakIsCooledByInjectionAndTheAccumulators() {
        PlantSim sim = running();
        sim.inject(FailureEvent.LOCA, 20.0);
        run(sim, 120);
        PlantSnapshot s = sim.snapshot();
        assertTrue(s.pressureBar() < 20.0, "blowdown: " + s.pressureBar());
        assertTrue(s.hpi() || s.lpi());
        assertTrue(s.accumulatorLeft() < 0.3, "the accumulators discharged");
        assertTrue(s.meltFraction() == 0.0, "the core did not melt");
        assertTrue(s.containmentBar() > 1.5 && s.containmentBar() < 5.5, "the containment holds: " + s.containmentBar());
    }

    @Test
    void aLargeBreakWithoutACCoolingDamagesTheCore() {
        PlantSim sim = running();
        sim.inject(FailureEvent.LOSS_OF_OFFSITE_POWER, 0);
        sim.inject(FailureEvent.DIESELS_FLOODED, 0);
        sim.inject(FailureEvent.LOCA, 20.0);
        run(sim, 800);
        PlantSnapshot s = sim.snapshot();
        assertTrue(s.claddingDamage() > 0.2 || s.meltFraction() > 0.0, "no injection, no cooling: damage " + s.claddingDamage());
    }

    @Test
    void aRodEjectionIsAPromptExcursionAndTheProtectionTripsAtOnce() {
        PlantSim sim = new PlantSim(PlantConfig.standard());
        sim.startAtPower(0.3);
        sim.step(20);
        sim.inject(FailureEvent.ROD_EJECTION, 2.2);
        boolean prompt = false;
        double peak = 0;
        for (int i = 0; i < 80; i++) {
            sim.step(0.05);
            prompt |= sim.snapshot().prompt();
            peak = Math.max(peak, sim.snapshot().power());
        }
        assertTrue(prompt, "the rod worth is above one dollar");
        assertTrue(peak > 1.0, "a power excursion: " + peak);
        assertTrue(sim.snapshot().tripped(), "the flux trip fired");
    }

    @Test
    void aFeedwaterLossIsSurvivedWithAuxiliaryFeedwater() {
        PlantSim sim = running();
        sim.inject(FailureEvent.FEEDWATER_LOSS, 0);
        run(sim, 120);
        PlantSnapshot s = sim.snapshot();
        assertTrue(s.tripped());
        assertTrue(s.afwRunning() || s.sgLevel() > 0.2);
        assertTrue(s.maxCladK() < 900.0);
    }

    @Test
    void aTurbineTripFromFullPowerTripsTheReactorTooAndTheSecondaryPressureStaysBounded() {
        PlantSim sim = running();
        sim.inject(FailureEvent.TURBINE_TRIP, 0);
        run(sim, 90);
        PlantSnapshot s = sim.snapshot();
        assertTrue(s.tripped(), "turbine trip above 50 percent power trips the reactor");
        assertTrue(s.secondaryBar() < 90.0, "the dump valves and the relief valves limit the pressure: " + s.secondaryBar());
        assertEquals(0.0, s.electricFraction(), 1e-9);
    }

    @Test
    void theLoadDemandSetsTheElectricOutput() {
        PlantSim sim = running();
        sim.setLoadDemand(0.5);
        run(sim, 120);
        assertTrue(sim.snapshot().electricFraction() < 0.8, "less load, less output: " + sim.snapshot().electricFraction());
    }

    @Test
    void aSteamGeneratorTubeRuptureReleasesActivityToTheSecondarySide() {
        PlantSim sim = running();
        sim.core.step(0.05, 1.0, 548.0, 155.0, 1.0, 1.0);
        sim.inject(FailureEvent.STEAM_GENERATOR_TUBE_RUPTURE, 0.02);
        assertEquals(0.02, sim.steam.sgtrLeak(), 1e-12);
    }

    @Test
    void sensorFailuresAreInjectedWithAFixedRandomSequence() {
        PlantSim a = new PlantSim(PlantConfig.hardcore()), b = new PlantSim(PlantConfig.hardcore());
        a.inject(FailureEvent.SENSOR_DEAD, 0);
        b.inject(FailureEvent.SENSOR_DEAD, 0);
        assertEquals(a.rps.faultyChannels(), b.rps.faultyChannels());
        assertEquals(1, a.rps.faultyChannels());
    }

    @Test
    void presetsDiffer() {
        assertTrue(PlantConfig.forgiving().timeCompression > PlantConfig.standard().timeCompression);
        assertTrue(PlantConfig.hardcore().poRvStuckChance > PlantConfig.standard().poRvStuckChance);
        assertTrue(PlantConfig.hardcore().dieselFuelHours < PlantConfig.standard().dieselFuelHours);
    }

    @Test
    void theSamePlantAndTheSameSeedGiveTheSameAccident() {
        PlantSim a = running(), b = running();
        a.inject(FailureEvent.PORV_STUCK_OPEN, 0);
        b.inject(FailureEvent.PORV_STUCK_OPEN, 0);
        run(a, 200);
        run(b, 200);
        assertEquals(a.snapshot().pressureBar(), b.snapshot().pressureBar(), 1e-9);
        assertEquals(a.snapshot().primaryMass(), b.snapshot().primaryMass(), 1e-9);
    }

    // ---------------------------------------------------------------- persistence

    @Test
    void saveAndLoadRoundTripOfTheWholePlant() {
        PlantSim a = running();
        a.inject(FailureEvent.PORV_STUCK_OPEN, 0);
        run(a, 100);
        double[] data = a.save();
        PlantSim b = new PlantSim(PlantConfig.standard());
        assertTrue(b.load(data));
        run(a, 60);
        run(b, 60);
        assertEquals(a.snapshot().pressureBar(), b.snapshot().pressureBar(), 0.5);
        assertEquals(a.snapshot().power(), b.snapshot().power(), 1e-3);
        assertEquals(a.snapshot().primaryMass(), b.snapshot().primaryMass(), 2e-3);
    }

    @Test
    void wrongSizeOrVersionIsIgnored() {
        PlantSim sim = new PlantSim(PlantConfig.standard());
        assertFalse(sim.load(new double[]{99, 1, 2}));
        assertFalse(sim.load(null));
        double[] data = running().save();
        data[0] = 1;
        assertFalse(sim.load(data));
    }

    // ---------------------------------------------------------------- the driven mode (the game owns the loops)

    @Test
    void inDrivenModeTheHeatSinkAndFlowComeFromTheOutside() {
        PlantSim sim = new PlantSim(PlantConfig.standard());
        sim.setMode(PlantSim.Mode.DRIVEN);
        sim.startAtPower(1.0);
        sim.setDriven(1.0, 1.0, 1.0, 155.0);
        run(sim, 200);
        PlantSnapshot ok = sim.snapshot();
        assertTrue(ok.power() > 0.8 && ok.power() < 1.15, "power " + ok.power());
        assertTrue(sim.electricFraction() > 0.8);

        sim.setDriven(1.0, 0.0, 1.0, 155.0);       // the heat sink is gone: the inlet temperature rises
        run(sim, 120);
        assertTrue(sim.snapshot().coldLegK() > ok.coldLegK() + 5.0, "the loop heats up: " + sim.snapshot().coldLegK());
        assertEquals(0.0, sim.electricFraction(), 1e-9);
    }

    @Test
    void inDrivenModeALostCoolantSupplyTripsAndOverheats() {
        PlantSim sim = new PlantSim(PlantConfig.standard());
        sim.setMode(PlantSim.Mode.DRIVEN);
        sim.startAtPower(1.0);
        sim.setDriven(0.0, 0.0, 0.2, 100.0);
        run(sim, 120);
        PlantSnapshot s = sim.snapshot();
        assertTrue(s.tripped(), "low flow and a core that is mostly uncovered trip the reactor");
        assertTrue(s.maxCladK() > 700.0);
    }
}
