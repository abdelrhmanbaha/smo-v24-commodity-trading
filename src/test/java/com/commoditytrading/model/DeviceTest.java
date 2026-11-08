package com.commoditytrading.model;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceTest {

    @Test
    void startAndCompleteServiceMovesDeviceAndRequestThroughTheirStates() {
        Device device = new Device(1, 2.0, new SplittableRandom(5));
        Request request = new Request(1, 1, 0.0);

        double end = device.startService(request, 3.0);

        assertFalse(device.isFree());
        assertEquals(DeviceStatus.BUSY, device.getStatus());
        assertSame(request, device.getCurrentRequest());
        assertEquals(RequestStatus.IN_SERVICE, request.getStatus());
        assertTrue(end > 3.0);

        Request finished = device.completeService();

        assertSame(request, finished);
        assertTrue(device.isFree());
        assertNull(device.getCurrentRequest());
        assertEquals(RequestStatus.SERVED, request.getStatus());
        assertEquals(end, request.getServiceEndTime());
    }

    @Test
    void busyTimeAccumulatesEveryServiceDuration() {
        Device device = new Device(1, 1.0, new SplittableRandom(8));

        double expected = 0.0;
        for (int i = 1; i <= 5; i++) {
            Request r = new Request(1, i, 0.0);
            double now = 10.0 * i;
            double end = device.startService(r, now);
            expected += end - now;
            device.completeService();
        }

        assertEquals(expected, device.getTotalBusyTime(), 1e-9);
    }

    @Test
    void busyDeviceRejectsASecondRequest() {
        Device device = new Device(1, 1.0, new SplittableRandom(1));
        device.startService(new Request(1, 1, 0.0), 0.0);

        assertThrows(IllegalStateException.class,
                () -> device.startService(new Request(1, 2, 0.0), 0.0));
    }

    @Test
    void freeDeviceHasNothingToComplete() {
        Device device = new Device(1, 1.0, new SplittableRandom(1));

        assertThrows(IllegalStateException.class, device::completeService);
    }

    @Test
    void meanServiceTimeApproachesOneOverLambda() {
        double lambda = 0.5;                        // mean service time = 2.0
        Device device = new Device(1, lambda, new SplittableRandom(3));

        int n = 100_000;
        for (int i = 1; i <= n; i++) {
            Request r = new Request(1, i, 0.0);
            device.startService(r, 0.0);
            device.completeService();
        }

        assertEquals(2.0, device.getTotalBusyTime() / n, 0.03);
    }

    @Test
    void invalidParametersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Device(0, 1.0, new SplittableRandom(1)));
        assertThrows(IllegalArgumentException.class, () -> new Device(1, 0.0, new SplittableRandom(1)));
    }
}