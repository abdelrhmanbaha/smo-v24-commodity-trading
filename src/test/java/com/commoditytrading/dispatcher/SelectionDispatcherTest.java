package com.commoditytrading.dispatcher;

import com.commoditytrading.buffer.Buffer;
import com.commoditytrading.model.Device;
import com.commoditytrading.model.Request;
import com.commoditytrading.model.RequestStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionDispatcherTest {

    private static List<Device> devices(int count) {
        List<Device> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(new Device(i, 1.0, new SplittableRandom(i)));
        }
        return list;
    }

    /** Finishes the device's current request and tells ДВ, like the simulator will. */
    private static List<Request> finish(SelectionDispatcher dv, Device device) {
        Request done = device.completeService();
        return dv.onDeviceFree(device, done.getServiceEndTime());
    }

    @Test
    void d2p2_directServiceGoesRoundTheDevicesFromThePointer() {
        List<Device> devs = devices(3);
        SelectionDispatcher dv = new SelectionDispatcher(new Buffer(5), devs);

        assertEquals(1, dv.getPointerDeviceId());
        assertTrue(dv.tryServeDirectly(new Request(1, 1, 0.0)));
        assertTrue(dv.tryServeDirectly(new Request(1, 2, 0.0)));
        assertTrue(dv.tryServeDirectly(new Request(1, 3, 0.0)));

        assertEquals("1.1", devs.get(0).getCurrentRequest().getLabel());
        assertEquals("1.2", devs.get(1).getCurrentRequest().getLabel());
        assertEquals("1.3", devs.get(2).getCurrentRequest().getLabel());
        assertEquals(1, dv.getPointerDeviceId());           // wrapped round after device 3
    }

    @Test
    void d2p2_searchSkipsBusyDevicesAndMovesThePointerPastTheChosenOne() {
        List<Device> devs = devices(4);
        SelectionDispatcher dv = new SelectionDispatcher(new Buffer(5), devs);
        devs.get(0).startService(new Request(5, 1, 0.0), 0.0);   // device 1 busy, pointer still at 1

        assertTrue(dv.tryServeDirectly(new Request(1, 1, 0.0)));

        assertEquals("1.1", devs.get(1).getCurrentRequest().getLabel());
        assertEquals(3, dv.getPointerDeviceId());
    }

    @Test
    void noDirectServiceWhenEveryDeviceIsBusy() {
        List<Device> devs = devices(2);
        SelectionDispatcher dv = new SelectionDispatcher(new Buffer(5), devs);
        dv.tryServeDirectly(new Request(1, 1, 0.0));
        dv.tryServeDirectly(new Request(1, 2, 0.0));

        assertFalse(dv.tryServeDirectly(new Request(1, 3, 0.0)));
    }

    @Test
    void d2b5_packetTakesEveryRequestOfTheHighestPrioritySourceOldestFirst() {
        Buffer buffer = new Buffer(10);
        List<Device> devs = devices(1);
        SelectionDispatcher dv = new SelectionDispatcher(buffer, devs);
        dv.tryServeDirectly(new Request(3, 1, 1.0));             // the only device is busy
        Request s2 = new Request(2, 1, 0.1);
        Request s1newer = new Request(1, 2, 0.4);
        Request s1older = new Request(1, 1, 0.2);
        buffer.placeRequest(s2);
        buffer.placeRequest(s1newer);
        buffer.placeRequest(s1older);

        List<Request> started = finish(dv, devs.get(0));

        assertEquals(List.of(s1older), started);                 // oldest member first
        assertEquals(List.of(s1newer), dv.getPendingBatchMembers());
        assertEquals(OptionalInt.of(1), dv.getActiveBatchSourceId());
        assertFalse(buffer.contains(s1newer));                   // members left the buffer at once
        assertTrue(buffer.contains(s2));                         // other sources stay
    }

    @Test
    void d2b5_packetIsFrozenLaterArrivalsOfItsSourceDoNotJoin() {
        Buffer buffer = new Buffer(10);
        List<Device> devs = devices(1);
        SelectionDispatcher dv = new SelectionDispatcher(buffer, devs);
        dv.tryServeDirectly(new Request(3, 1, 1.0));
        Request a = new Request(1, 1, 0.1);
        Request b = new Request(1, 2, 0.2);
        buffer.placeRequest(a);
        buffer.placeRequest(b);
        finish(dv, devs.get(0));                                 // packet [a, b]; a starts

        Request late = new Request(1, 3, 0.3);                   // same source, arrives after
        buffer.placeRequest(late);

        assertEquals(List.of(b), finish(dv, devs.get(0)));       // b, not the late request
        assertTrue(buffer.contains(late));
        assertEquals(List.of(late), finish(dv, devs.get(0)));    // only now: a new packet [late]
    }

    @Test
    void d2b5_nextPacketWaitsUntilEveryMemberHasFinishedThenFillsAllFreeDevices() {
        Buffer buffer = new Buffer(10);
        List<Device> devs = devices(2);
        SelectionDispatcher dv = new SelectionDispatcher(buffer, devs);
        dv.tryServeDirectly(new Request(5, 1, 1.0));             // device 1
        dv.tryServeDirectly(new Request(5, 2, 1.0));             // device 2
        Request s1a = new Request(1, 1, 0.1);
        Request s1b = new Request(1, 2, 0.2);
        buffer.placeRequest(s1a);
        buffer.placeRequest(s1b);

        finish(dv, devs.get(0));                                 // packet [s1a, s1b]; s1a on device 1
        finish(dv, devs.get(1));                                 // s1b on device 2
        Request s2a = new Request(2, 1, 0.3);
        Request s2b = new Request(2, 2, 0.4);
        buffer.placeRequest(s2a);
        buffer.placeRequest(s2b);

        List<Request> afterFirst = finish(dv, devs.get(0));      // s1b still in service on device 2

        assertTrue(afterFirst.isEmpty());                         // no new packet yet
        assertTrue(devs.get(0).isFree());
        assertEquals(OptionalInt.of(1), dv.getActiveBatchSourceId());
        assertFalse(dv.tryServeDirectly(new Request(4, 1, 1.0))); // no bypass while active

        List<Request> afterLast = finish(dv, devs.get(1));       // packet fully processed

        assertEquals(List.of(s2a, s2b), afterLast);               // both free devices get members
        assertFalse(devs.get(0).isFree());
        assertFalse(devs.get(1).isFree());
    }

    @Test
    void freedDeviceStaysIdleWhenTheBufferIsEmpty() {
        List<Device> devs = devices(1);
        SelectionDispatcher dv = new SelectionDispatcher(new Buffer(5), devs);
        dv.tryServeDirectly(new Request(1, 1, 0.0));

        assertTrue(finish(dv, devs.get(0)).isEmpty());
        assertTrue(devs.get(0).isFree());
        assertEquals(OptionalInt.empty(), dv.getActiveBatchSourceId());
    }

    @Test
    void afterAPacketEndsWithAnEmptyBufferDirectServiceWorksAgain() {
        Buffer buffer = new Buffer(5);
        List<Device> devs = devices(1);
        SelectionDispatcher dv = new SelectionDispatcher(buffer, devs);
        dv.tryServeDirectly(new Request(3, 1, 1.0));
        buffer.placeRequest(new Request(1, 1, 0.1));
        finish(dv, devs.get(0));                                 // packet [1.1]
        finish(dv, devs.get(0));                                 // packet done, buffer empty

        Request next = new Request(2, 1, 5.0);

        assertTrue(dv.tryServeDirectly(next));
        assertEquals(RequestStatus.IN_SERVICE, next.getStatus());
    }
}