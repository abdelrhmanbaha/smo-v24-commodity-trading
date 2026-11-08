package com.commoditytrading.statistics;

import com.commoditytrading.model.Device;
import com.commoditytrading.model.Request;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatisticsTest {

    private static Request served(int source, int number, double generated, double start, double end) {
        Request r = new Request(source, number, generated);
        r.markServiceStarted(start);
        r.markServed(end);
        return r;
    }

    @Test
    void refusalProbabilityIsRefusedOverGenerated() {
        Statistics stats = new Statistics(5);
        for (int i = 1; i <= 4; i++) {
            stats.recordArrival(new Request(3, i, i));
        }
        Request refused = new Request(3, 5, 5.0);
        stats.recordArrival(refused);
        refused.markRefused(6.0);
        stats.recordRefusal(refused);

        assertEquals(5, stats.generatedCount(3));
        assertEquals(1, stats.refusedCount(3));
        assertEquals(0.2, stats.refusalProbability(3), 1e-12);
    }

    @Test
    void meansAndVariancesComeFromServedRequests() {
        Statistics stats = new Statistics(2);
        // waiting 1 and 3, service 2 and 4
        Request a = served(1, 1, 0.0, 1.0, 3.0);
        Request b = served(1, 2, 0.0, 3.0, 7.0);
        stats.recordArrival(a);
        stats.recordArrival(b);
        stats.recordServiceEnd(a);
        stats.recordServiceEnd(b);

        assertEquals(2, stats.servedCount(1));
        assertEquals(2.0, stats.meanWaitingTime(1), 1e-12);
        assertEquals(3.0, stats.meanServiceTime(1), 1e-12);
        assertEquals(5.0, stats.meanTimeInSystem(1), 1e-12);
        assertEquals(2.0, stats.waitingTimeVariance(1), 1e-12);   // sample variance of {1, 3}
        assertEquals(2.0, stats.serviceTimeVariance(1), 1e-12);   // sample variance of {2, 4}
    }

    @Test
    void statisticsAreKeptSeparatelyPerSource() {
        Statistics stats = new Statistics(2);
        stats.recordArrival(new Request(1, 1, 0.0));
        stats.recordArrival(new Request(2, 1, 0.0));
        stats.recordArrival(new Request(2, 2, 0.0));

        assertEquals(1, stats.generatedCount(1));
        assertEquals(2, stats.generatedCount(2));
        assertEquals(3, stats.totalGenerated());
    }

    @Test
    void overallRefusalProbabilityCombinesAllSources() {
        Statistics stats = new Statistics(2);
        Request r1 = new Request(1, 1, 0.0);
        Request r2 = new Request(2, 1, 0.0);
        Request r3 = new Request(2, 2, 0.0);
        Request r4 = new Request(2, 3, 0.0);
        for (Request r : new Request[]{r1, r2, r3, r4}) {
            stats.recordArrival(r);
        }
        r4.markRefused(1.0);
        stats.recordRefusal(r4);

        assertEquals(0.25, stats.overallRefusalProbability(), 1e-12);
    }

    @Test
    void emptySourceReportsNaNInsteadOfAFakeZero() {
        Statistics stats = new Statistics(1);

        assertTrue(Double.isNaN(stats.refusalProbability(1)));
        assertTrue(Double.isNaN(stats.meanTimeInSystem(1)));
    }

    @Test
    void utilizationIsBusyTimeOverRunTime() {
        Device device = new Device(1, 1.0, new SplittableRandom(4));
        Request r = new Request(1, 1, 0.0);
        double end = device.startService(r, 0.0);
        device.completeService();

        assertEquals(0.5, Statistics.utilization(device, 2 * end), 1e-12);
    }

    @Test
    void recordingAnEventInTheWrongStateFails() {
        Statistics stats = new Statistics(1);
        Request waiting = new Request(1, 1, 0.0);

        assertThrows(IllegalStateException.class, () -> stats.recordServiceEnd(waiting));
        assertThrows(IllegalStateException.class, () -> stats.recordRefusal(waiting));
        assertThrows(IllegalArgumentException.class, () -> stats.generatedCount(2));
    }
}