package com.commoditytrading.model;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceTest {

    @Test
    void requestsAreNumberedPerSourceAndArriveAtTheScheduledTime() {
        Source source = new Source(3, "Logistics System", 1.0, 2.0, new SplittableRandom(42));

        double firstArrival = source.getNextArrivalTime();
        Request first = source.generateNextRequest();
        Request second = source.generateNextRequest();

        assertEquals(3, first.getSourceId());
        assertEquals(1, first.getSequenceNumber());
        assertEquals(2, second.getSequenceNumber());
        assertEquals(firstArrival, first.getGenerationTime());
        assertEquals(2, source.getGeneratedCount());
    }

    @Test
    void everyIntervalLiesWithinTheUniformBounds() {
        Source source = new Source(1, "Buyer System", 1.0, 3.0, new SplittableRandom(7));

        double previous = 0.0;
        for (int i = 0; i < 10_000; i++) {
            Request r = source.generateNextRequest();
            double interval = r.getGenerationTime() - previous;
            assertTrue(interval >= 1.0 && interval < 3.0, "interval out of [1, 3): " + interval);
            previous = r.getGenerationTime();
        }
    }

    @Test
    void meanIntervalApproachesTheMiddleOfTheRange() {
        Source source = new Source(1, "Buyer System", 1.0, 3.0, new SplittableRandom(11));

        int n = 100_000;
        Request last = null;
        for (int i = 0; i < n; i++) {
            last = source.generateNextRequest();
        }
        double observedMean = last.getGenerationTime() / n;

        assertEquals(2.0, source.getMeanInterval());
        assertEquals(2.0, observedMean, 0.02);
    }

    @Test
    void sameSeedGivesTheSameArrivals() {
        Source a = new Source(1, "Buyer System", 0.5, 1.5, new SplittableRandom(99));
        Source b = new Source(1, "Buyer System", 0.5, 1.5, new SplittableRandom(99));

        for (int i = 0; i < 100; i++) {
            assertEquals(a.generateNextRequest().getGenerationTime(),
                    b.generateNextRequest().getGenerationTime());
        }
    }

    @Test
    void priorityIsTheSourceNumber() {
        Source source = new Source(4, "Warehouse/Inventory System", 1.0, 2.0, new SplittableRandom(1));

        assertEquals(4, source.getPriority());
    }

    @Test
    void invalidParametersAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Source(0, "x", 1.0, 2.0, new SplittableRandom(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new Source(1, "x", 2.0, 2.0, new SplittableRandom(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new Source(1, "x", -1.0, 2.0, new SplittableRandom(1)));
    }
}