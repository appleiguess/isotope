package dev.isotopelib.sim.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The protection system, the electrical supply, the containment and the source term. */
class SystemsTest {

    private static Protection.Readings normal() {
        return new Protection.Readings();
    }

    // ---------------------------------------------------------------- protection

    @Test
    void oneChannelDemandingDoesNotTripTwoDo() {
        Protection rps = new Protection(PlantConfig.standard());
        Protection.Readings r = normal();
        r.flux = 1.0;
        rps.setFault(Protection.Param.FLUX_HIGH, 0, Protection.Fault.STUCK_HIGH, 0);
        rps.evaluate(0.05, r);
        assertFalse(rps.tripped(), "one lying channel does not trip the reactor (2 out of 4)");
        rps.setFault(Protection.Param.FLUX_HIGH, 1, Protection.Fault.STUCK_HIGH, 0);
        rps.evaluate(0.05, r);
        assertTrue(rps.tripped(), "two channels trip it");
    }

    @Test
    void aRealHighFluxTripsAndTheTripLatches() {
        Protection rps = new Protection(PlantConfig.standard());
        Protection.Readings r = normal();
        r.flux = 1.2;
        rps.evaluate(0.05, r);
        assertTrue(rps.tripped());
        r.flux = 1.0;
        rps.evaluate(0.05, r);
        assertTrue(rps.tripped(), "the trip stays until the operator resets it");
        rps.reset();
        rps.evaluate(0.05, r);
        assertFalse(rps.tripped());
    }

    @Test
    void aBypassedChannelMakesTheVoteTwoOutOfThree() {
        Protection rps = new Protection(PlantConfig.standard());
        rps.setBypass(0, true);
        assertTrue(rps.anyBypass());
        Protection.Readings r = normal();
        r.pressureBar = 175.0;
        rps.evaluate(0.05, r);
        assertTrue(rps.tripped(), "a real high pressure still trips with one channel bypassed");
    }

    @Test
    void deadSensorsFailSafeAndLowPowerPermissivesBlockSomeTrips() {
        Protection rps = new Protection(PlantConfig.standard());
        rps.setFault(Protection.Param.PRESSURE_LOW, 0, Protection.Fault.DEAD, 0);
        rps.setFault(Protection.Param.PRESSURE_LOW, 1, Protection.Fault.DEAD, 0);
        Protection.Readings r = normal();
        r.thermalPower = 1.0;
        rps.evaluate(0.05, r);
        assertTrue(rps.tripped(), "dead channels demand a trip at power");

        Protection low = new Protection(PlantConfig.standard());
        Protection.Readings lr = normal();
        lr.thermalPower = 0.01;
        lr.flux = 0.01;
        lr.pressureBar = 100.0;
        low.evaluate(0.05, lr);
        assertFalse(low.tripped(), "the low pressure trip is blocked at low power (P-7)");
    }

    @Test
    void lossOfDirectCurrentTripsTheReactor() {
        Protection rps = new Protection(PlantConfig.standard());
        Protection.Readings r = normal();
        r.dcAvailable = false;
        rps.evaluate(0.05, r);
        assertTrue(rps.tripped());
    }

    @Test
    void safeguardsStartOnLowPressureAndHighContainmentPressure() {
        Protection rps = new Protection(PlantConfig.standard());
        Protection.Readings r = normal();
        r.pressureBar = 100.0;
        rps.evaluate(0.05, r);
        assertTrue(rps.si());
        Protection p2 = new Protection(PlantConfig.standard());
        Protection.Readings r2 = normal();
        r2.containmentBar = 2.6;
        p2.evaluate(0.05, r2);
        assertTrue(p2.spray());
        assertTrue(p2.si());
    }

    @Test
    void manualTripAlwaysWorks() {
        Protection rps = new Protection(PlantConfig.standard());
        rps.manualTrip();
        rps.evaluate(0.05, normal());
        assertTrue(rps.tripped());
        assertNotEquals(0, rps.tripMask());
    }

    // ---------------------------------------------------------------- electrical

    @Test
    void dieselsStartAfterALossOfOffsitePower() {
        PlantConfig cfg = PlantConfig.standard();
        Electrical e = new Electrical(cfg);
        assertTrue(e.acAvailable());
        e.setOffsite(false);
        e.step(1.0);
        assertFalse(e.acAvailable(), "no AC while the diesels start");
        assertTrue(e.dcAvailable(), "the batteries carry the DC");
        for (int i = 0; i < 12; i++)
            e.step(1.0);
        assertTrue(e.acAvailable());
        assertEquals(cfg.diesels, e.runningDiesels());
    }

    @Test
    void floodedDieselsMakeAStationBlackoutAndTheBatteriesRunDown() {
        PlantConfig cfg = PlantConfig.standard();
        Electrical e = new Electrical(cfg);
        e.setOffsite(false);
        e.floodDiesels();
        for (int i = 0; i < 20; i++)
            e.step(1.0);
        assertTrue(e.stationBlackout());
        double b0 = e.battery();
        for (int i = 0; i < 2000; i++)
            e.step(1.0);
        assertTrue(e.battery() < b0, "the battery is drained");
        assertFalse(e.dcAvailable(), "and after the compressed hours it is empty");
    }

    @Test
    void dieselsRunOutOfFuel() {
        PlantConfig cfg = PlantConfig.standard();
        cfg.dieselFuelHours = 1.0;
        Electrical e = new Electrical(cfg);
        e.setOffsite(false);
        for (int i = 0; i < 400; i++)
            e.step(1.0);
        assertEquals(0, e.runningDiesels());
        e.refuelDiesels();
        e.step(1.0);
        assertTrue(e.dieselFuel() > 0.9);
    }

    // ---------------------------------------------------------------- containment

    @Test
    void steamRaisesThePressureAndCoolingBringsItDown() {
        PlantConfig cfg = PlantConfig.standard();
        Containment c = new Containment(cfg, new Rng(1));
        for (int i = 0; i < 200; i++)
            c.step(0.5, 0.5, 0.0, false, true);
        double high = c.pressure();
        assertTrue(high > 1.5, "pressure " + high);
        for (int i = 0; i < 600; i++)
            c.step(0.5, 0.0, 0.0, false, true);
        assertTrue(c.pressure() < high, "the structures condense the steam");
    }

    @Test
    void theSprayCoolsFasterThanTheStructures() {
        PlantConfig cfg = PlantConfig.standard();
        Containment a = new Containment(cfg, new Rng(1)), b = new Containment(cfg, new Rng(1));
        for (int i = 0; i < 100; i++) {
            a.step(0.5, 1.0, 0.0, false, true);
            b.step(0.5, 1.0, 0.0, true, true);
        }
        assertTrue(b.pressure() < a.pressure());
    }

    @Test
    void hydrogenBurnsAboveTheThresholdAndTheRecombinersWorkBelowIt() {
        PlantConfig cfg = PlantConfig.standard();
        Containment low = new Containment(cfg, new Rng(3));
        low.step(0.5, 0.0, 120.0, false, true);          // about 2.5 percent
        double pct = low.hydrogenPercentVol();
        assertTrue(pct > 1.0 && pct < cfg.hydrogenBurnPercent, "percent " + pct);
        for (int i = 0; i < 4000; i++)
            low.step(0.5, 0.0, 0.0, false, true);
        assertTrue(low.hydrogenPercentVol() < pct, "the recombiners remove it");
        assertFalse(low.breached());

        Containment high = new Containment(cfg, new Rng(3));
        high.step(0.5, 0.0, 300.0, false, false);        // more than 4 percent and no power for the recombiners
        boolean burned = false;
        double peak = 1.0;
        for (int i = 0; i < 2000 && !burned; i++) {
            high.step(0.5, 0.0, 0.0, false, false);
            burned |= high.burned();
            peak = Math.max(peak, high.pressure());
        }
        assertTrue(burned, "a burn happened");
        assertTrue(peak > 1.5, "with a pressure spike " + peak);
    }

    @Test
    void aVeryHighPressureBreaksTheBuildingAndAFilteredVentRelievesIt() {
        PlantConfig cfg = PlantConfig.standard();
        Containment c = new Containment(cfg, new Rng(1));
        for (int i = 0; i < 2000 && !c.breached(); i++)
            c.step(0.5, 3.0, 0.0, false, false);
        assertTrue(c.breached() || c.integrity() < 1.0, "over-pressure damages the building");

        Containment v = new Containment(cfg, new Rng(1));
        for (int i = 0; i < 60; i++)
            v.step(0.5, 3.0, 0.0, false, true);
        double before = v.pressure();
        v.openVent(true);
        for (int i = 0; i < 200; i++)
            v.step(0.5, 0.0, 0.0, false, true);
        assertTrue(v.pressure() < before, "the vent lowers the pressure");
    }

    // ---------------------------------------------------------------- source term and INES

    @Test
    void releaseFollowsTheBarriersAndTheFilteredVentReleasesFarLess() {
        PlantConfig cfg = PlantConfig.standard();
        SourceTerm open = new SourceTerm(cfg), vented = new SourceTerm(cfg);
        for (int i = 0; i < 400; i++) {
            open.step(0.5, 1.0, 0.0, 0.5, 0.02, 0.8, false, 0.01, 0.0, 0.0, 0.0, false);
            vented.step(0.5, 1.0, 0.0, 0.5, 0.02, 0.8, false, 1e-6, 0.01, 0.0, 0.0, false);
        }
        assertTrue(open.environment() > 0, "a leaking containment releases");
        assertTrue(vented.environment() < open.environment() * 0.1, "the filtered vent releases far less");
        assertTrue(open.environment() < 1.0);
    }

    @Test
    void inesLevelsRiseWithTheDamage() {
        PlantConfig cfg = PlantConfig.standard();
        SourceTerm s = new SourceTerm(cfg);
        assertEquals(0, s.inesLevel(0, 0, false, false, false, false));
        assertEquals(1, s.inesLevel(0, 0, false, false, false, true));
        assertEquals(2, s.inesLevel(0, 0, false, false, true, true));
        assertEquals(3, s.inesLevel(0, 0.1, false, false, false, false));
        assertEquals(4, s.inesLevel(0.05, 0.5, false, false, false, false));
        assertEquals(5, s.inesLevel(0.5, 1.0, true, false, false, false));
    }

    @Test
    void aSteamGeneratorTubeRuptureReleasesThroughTheSecondarySide() {
        PlantConfig cfg = PlantConfig.standard();
        SourceTerm s = new SourceTerm(cfg);
        s.step(0.5, 1.0, 0.0, 0.2, 0.0, 1.0, false, 0.0, 0.0, 0.0, 0.0, false);
        double primary = s.primary();
        assertTrue(primary > 0);
        for (int i = 0; i < 200; i++)
            s.step(0.5, 1.0, 0.0, 0.2, 0.0, 1.0, false, 0.0, 0.0, 0.05, 1.0, false);
        assertTrue(s.environment() > 0, "the relief valves of the secondary side let it out");
    }
}
