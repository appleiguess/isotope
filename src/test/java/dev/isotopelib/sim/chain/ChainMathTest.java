package dev.isotopelib.sim.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChainMathTest {
    @Test
    void coolantBelowBoilingGivesNothing() {
        assertEquals(0.0, ChainMath.exchangerOutput(90, 1000, 1000, 2400, 0.9, 2.0, 300, 1.0), 1e-9);
    }

    @Test
    void outputRisesWithTemperature() {
        double cool = ChainMath.exchangerOutput(200, 400, 5000, 2400, 0.9, 2.0, 300, 1.0);
        double hot = ChainMath.exchangerOutput(300, 400, 5000, 2400, 0.9, 2.0, 300, 1.0);
        assertTrue(hot > cool && cool > 0);
    }

    @Test
    void fullTankStopsTheGenerator() {
        assertEquals(0.0, ChainMath.exchangerOutput(300, 1000, 0, 2400, 0.9, 2.0, 300, 1.0), 1e-9);
    }

    @Test
    void ratingLimitsTheGenerator() {
        assertEquals(1200.0, ChainMath.exchangerOutput(300, 100000, 100000, 2400, 1.0, 2.0, 300, 0.5), 1e-6);
    }

    @Test
    void sixGeneratorsCarryTheFullPlant() {
        double atFull = 6 * 2400.0;
        assertEquals(1.0, ChainMath.heatSink(atFull, atFull, 1.0), 1e-9);
        assertEquals(0.5, ChainMath.heatSink(atFull / 2, atFull, 1.0), 1e-9);
        assertEquals(6, ChainMath.unitsForFullPower(atFull, 2400.0));
    }

    @Test
    void aColdCoreNeedsNoSink() {
        assertEquals(1.0, ChainMath.heatSink(0.0, 14400.0, 0.0), 1e-9);
        assertEquals(0.0, ChainMath.heatSink(0.0, 14400.0, 0.5), 1e-9);
    }

    @Test
    void turbineSpinsUpOverSecondsNotInstantly() {
        double f = 0.0;
        f = ChainMath.turbineLag(f, 1.0, 6.0, 0.05);
        assertTrue(f > 0.0 && f < 0.02);
        for (int i = 0; i < 20 * 6; i++)
            f = ChainMath.turbineLag(f, 1.0, 6.0, 0.05);
        assertTrue(f > 0.55 && f < 0.7, "after one time constant about 63 percent: " + f);
        for (int i = 0; i < 20 * 40; i++)
            f = ChainMath.turbineLag(f, 1.0, 6.0, 0.05);
        assertTrue(f > 0.99);
    }

    @Test
    void turbineCoastsDownSlowerThanItSpinsUp() {
        double up = 0.0, down = 1.0;
        for (int i = 0; i < 20 * 6; i++) {
            up = ChainMath.turbineLag(up, 1.0, 6.0, 0.05);
            down = ChainMath.turbineCoast(down, 6.0, 0.05);
        }
        assertTrue(1.0 - down < up, "coasting down is slower than spinning up");
        assertTrue(down > 0.4 && down < 0.7);
    }

    @Test
    void sixTurbinesGiveTheBaseOutput() {
        assertEquals(2_200_000.0, 6 * ChainMath.stressUnits(2_200_000.0, 6, 1.0), 1e-3);
        assertEquals(1_100_000.0, 6 * ChainMath.stressUnits(2_200_000.0, 6, 0.5), 1e-3);
        double perRpm = ChainMath.capacityPerRpm(2_200_000.0, 6, 256.0);
        assertEquals(2_200_000.0 / 6, perRpm * 256.0, 1e-3);
    }

    @Test
    void aBlockedVentPushesNothing() {
        assertEquals(0.0, ChainMath.ventPush(4000, 2400, 1.0, false), 1e-9);
        assertEquals(2400.0, ChainMath.ventPush(4000, 2400, 1.0, true), 1e-9);
        assertEquals(100.0, ChainMath.ventPush(100, 2400, 1.0, true), 1e-9);
    }

    @Test
    void backPressureOnlyWhenTheSteamSideIsNearlyFull() {
        assertEquals(0.0, ChainMath.backPressureBar(0.5, 40.0), 1e-9);
        assertEquals(0.0, ChainMath.backPressureBar(0.8, 40.0), 1e-9);
        assertEquals(20.0, ChainMath.backPressureBar(0.9, 40.0), 1e-9);
        assertEquals(40.0, ChainMath.backPressureBar(1.0, 40.0), 1e-9);
    }

    @Test
    void steamPressureGrowsWithTheFill() {
        assertEquals(1.0, ChainMath.steamPressureBar(0.0, 61.0), 1e-9);
        assertEquals(61.0, ChainMath.steamPressureBar(1.0, 61.0), 1e-9);
    }

    @Test
    void condensationGrowsWithVolumeAndIce() {
        assertEquals(0.0, ChainMath.condenseRate(0, 3, 20, 120), 1e-9);
        assertEquals(160.0, ChainMath.condenseRate(8, 0, 20, 120), 1e-9);
        assertEquals(400.0, ChainMath.condenseRate(8, 2, 20, 120), 1e-9);
        assertEquals(160.0, ChainMath.condenseRate(8, -4, 20, 120), 1e-9);
    }
}
