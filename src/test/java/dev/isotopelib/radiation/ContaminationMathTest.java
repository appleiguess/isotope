package dev.isotopelib.radiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ContaminationMathTest {
    @Test
    void dustBuildsUpAndStays() {
        double bq = 0;
        for (int s = 0; s < 60; s++)  // a minute in fallout of 10 µSv/s
            bq = ContaminationMath.afterSecond(bq, 10 * ContaminationMath.DEPOSIT_PER_FALLOUT, 0);
        assertTrue(bq > 2.5e7 && bq < 3.0e7, "after a minute " + bq);
        double left = bq;
        for (int s = 0; s < 60; s++)  // a minute away from it, not washed
            left = ContaminationMath.afterSecond(left, 0, 0);
        assertTrue(left > 0.85 * bq, "most of it stays: " + left / bq);
    }

    @Test
    void waterWashesItOffInSeconds() {
        double bq = 3.0e7;
        int s = 0;
        while (bq > 0 && s < 100) {
            bq = ContaminationMath.afterSecond(bq, 0, ContaminationMath.WASH_WATER);
            s++;
        }
        assertEquals(0, bq);
        assertTrue(s < 30, "gone after " + s + " s in water");
    }

    @Test
    void doseIsAFractionOfTheFalloutItCameFrom() {
        // a minute in fallout of 10 µSv/s leaves dust that gives about a tenth of that once you walk out
        double bq = 0;
        for (int s = 0; s < 60; s++)
            bq = ContaminationMath.afterSecond(bq, 10 * ContaminationMath.DEPOSIT_PER_FALLOUT, 0);
        double[] d = ContaminationMath.doseRate(bq, 20000, true);
        double total = d[0] + d[1] + d[2];
        assertTrue(total > 0.4 && total < 2.0, "µSv/s " + total);
        double[] masked = ContaminationMath.doseRate(bq, 20000, false);
        assertEquals(0, masked[2]);
        assertTrue(masked[1] > masked[0], "dust is mostly beta");
    }

    @Test
    void specksGrowWithTheDust() {
        assertEquals(0, ContaminationMath.specks(1e3));
        assertEquals(1, ContaminationMath.specks(2e4));
        int last = 0;
        for (double bq = 1e4; bq < 1e12; bq *= 3) {
            int n = ContaminationMath.specks(bq);
            assertTrue(n >= last && n <= 5);
            last = n;
        }
        assertEquals(5, last);
    }
}
