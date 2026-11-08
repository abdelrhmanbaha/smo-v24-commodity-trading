package com.commoditytrading.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestTest {

    @Test
    void newRequestIsWaitingAndLabelledSourceDotNumber() {
        Request r = new Request(2, 3, 1.5);

        assertEquals(RequestStatus.WAITING, r.getStatus());
        assertEquals("2.3", r.getLabel());
        assertEquals(2, r.getSourceId());
        assertEquals(1.5, r.getGenerationTime());
    }

    @Test
    void servedRequestReportsWaitingServiceAndSystemTimes() {
        Request r = new Request(1, 1, 2.0);

        r.markServiceStarted(5.0);
        r.markServed(9.0);

        assertEquals(RequestStatus.SERVED, r.getStatus());
        assertEquals(3.0, r.getWaitingTime());     // 5 - 2
        assertEquals(4.0, r.getServiceTime());     // 9 - 5
        assertEquals(7.0, r.getTimeInSystem());    // 9 - 2
    }

    @Test
    void directlyServedRequestHasZeroWaitingTime() {
        Request r = new Request(1, 1, 2.0);

        r.markServiceStarted(2.0);

        assertEquals(0.0, r.getWaitingTime());
    }

    @Test
    void waitingRequestCanBeRefused() {
        Request r = new Request(5, 1, 1.0);

        r.markRefused(4.0);

        assertEquals(RequestStatus.REFUSED, r.getStatus());
        assertEquals(4.0, r.getRefusalTime());
    }

    @Test
    void requestInServiceCannotBeRefused() {
        Request r = new Request(5, 1, 1.0);
        r.markServiceStarted(1.0);

        assertThrows(IllegalStateException.class, () -> r.markRefused(2.0));
    }

    @Test
    void requestCannotBeServedTwice() {
        Request r = new Request(1, 1, 0.0);
        r.markServiceStarted(1.0);
        r.markServed(2.0);

        assertThrows(IllegalStateException.class, () -> r.markServed(3.0));
    }

    @Test
    void serviceCannotStartBeforeGeneration() {
        Request r = new Request(1, 1, 5.0);

        assertThrows(IllegalArgumentException.class, () -> r.markServiceStarted(4.0));
    }

    @Test
    void timesAreUnavailableBeforeService() {
        Request r = new Request(1, 1, 0.0);

        assertThrows(IllegalStateException.class, r::getWaitingTime);
        assertThrows(IllegalStateException.class, r::getTimeInSystem);
    }

    @Test
    void invalidIdentityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Request(0, 1, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new Request(1, 0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new Request(1, 1, -1.0));
    }
}