package com.commoditytrading.dispatcher;

import com.commoditytrading.buffer.Buffer;
import com.commoditytrading.model.Request;
import com.commoditytrading.model.RequestStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementDispatcherTest {

    @Test
    void d1oz3_requestIsPlacedWhenTheBufferHasRoom() {
        Buffer buffer = new Buffer(2);
        PlacementDispatcher dp = new PlacementDispatcher(buffer);
        Request r = new Request(1, 1, 0.0);

        Optional<Request> refused = dp.onRequestArrival(r);

        assertTrue(refused.isEmpty());
        assertTrue(buffer.contains(r));
        assertEquals(RequestStatus.WAITING, r.getStatus());
    }

    @Test
    void d1oo2_fullBufferRefusesTheLowestPriorityRequestAndKeepsTheNewOne() {
        Buffer buffer = new Buffer(3);
        PlacementDispatcher dp = new PlacementDispatcher(buffer);
        Request s1 = new Request(1, 1, 0.0);
        Request s5 = new Request(5, 1, 0.1);
        Request s3 = new Request(3, 1, 0.2);
        dp.onRequestArrival(s1);
        dp.onRequestArrival(s5);
        dp.onRequestArrival(s3);
        Request incoming = new Request(2, 1, 1.0);

        Optional<Request> refused = dp.onRequestArrival(incoming);

        assertSame(s5, refused.orElseThrow());
        assertEquals(RequestStatus.REFUSED, s5.getStatus());
        assertEquals(1.0, s5.getRefusalTime());
        assertFalse(buffer.contains(s5));
        assertTrue(buffer.contains(incoming));
        assertSame(incoming, buffer.snapshot().get(1));   // takes the victim's slot (Д1ОЗ3)
        assertTrue(buffer.isFull());
    }

    @Test
    void d1oo2_aBufferedRequestIsRefusedEvenIfTheNewcomerHasLowerPriority() {
        Buffer buffer = new Buffer(2);
        PlacementDispatcher dp = new PlacementDispatcher(buffer);
        Request s1 = new Request(1, 1, 0.0);
        Request s2 = new Request(2, 1, 0.1);
        dp.onRequestArrival(s1);
        dp.onRequestArrival(s2);
        Request incoming = new Request(5, 1, 1.0);

        Optional<Request> refused = dp.onRequestArrival(incoming);

        assertSame(s2, refused.orElseThrow());
        assertTrue(buffer.contains(incoming));
        assertEquals(RequestStatus.WAITING, incoming.getStatus());
    }

    @Test
    void onlyAWaitingRequestCanBePlaced() {
        PlacementDispatcher dp = new PlacementDispatcher(new Buffer(2));
        Request r = new Request(1, 1, 0.0);
        r.markServiceStarted(0.0);

        assertThrows(IllegalStateException.class, () -> dp.onRequestArrival(r));
    }
}