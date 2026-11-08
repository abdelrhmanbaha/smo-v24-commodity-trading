package com.commoditytrading.buffer;

import com.commoditytrading.model.Request;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BufferTest {

    @Test
    void requestsFillSlotsFromTheStart() {
        Buffer buffer = new Buffer(3);
        Request a = new Request(1, 1, 0.0);
        Request b = new Request(2, 1, 0.1);

        buffer.placeRequest(a);
        buffer.placeRequest(b);

        List<Request> slots = buffer.snapshot();
        assertSame(a, slots.get(0));
        assertSame(b, slots.get(1));
        assertNull(slots.get(2));
    }

    @Test
    void d1oz3_freedSlotIsReusedFirstAndNothingShifts() {
        Buffer buffer = new Buffer(4);
        Request a = new Request(1, 1, 0.0);
        Request b = new Request(2, 1, 0.1);
        Request c = new Request(3, 1, 0.2);
        Request d = new Request(4, 1, 0.3);
        buffer.placeRequest(a);
        buffer.placeRequest(b);
        buffer.placeRequest(c);

        buffer.remove(b);                 // slot 1 becomes free; c stays in slot 2
        buffer.placeRequest(d);           // d must take slot 1, the first free slot

        List<Request> slots = buffer.snapshot();
        assertSame(a, slots.get(0));
        assertSame(d, slots.get(1));
        assertSame(c, slots.get(2));
        assertNull(slots.get(3));
    }

    @Test
    void fullBufferRejectsPlacement() {
        Buffer buffer = new Buffer(2);
        buffer.placeRequest(new Request(1, 1, 0.0));
        buffer.placeRequest(new Request(1, 2, 0.1));

        assertTrue(buffer.isFull());
        assertFalse(buffer.placeRequest(new Request(1, 3, 0.2)));
    }

    @Test
    void sizeEmptyAndFullAreConsistent() {
        Buffer buffer = new Buffer(20);

        assertTrue(buffer.isEmpty());
        assertEquals(0, buffer.size());
        assertEquals(20, buffer.capacity());

        for (int i = 1; i <= 20; i++) {
            buffer.placeRequest(new Request(1, i, i));
        }

        assertTrue(buffer.isFull());
        assertEquals(20, buffer.size());
    }

    @Test
    void d1oo2_lowestPriorityIsTheLargestSourceNumber() {
        Buffer buffer = new Buffer(5);
        buffer.placeRequest(new Request(1, 1, 0.0));
        Request fromSource5 = new Request(5, 1, 0.1);
        buffer.placeRequest(fromSource5);
        buffer.placeRequest(new Request(2, 1, 0.2));

        assertSame(fromSource5, buffer.findLowestPriorityRequest());
    }

    @Test
    void d1oo2_tieBreakChoosesTheNewestOfTheLowestPrioritySource() {
        Buffer buffer = new Buffer(5);
        Request older = new Request(4, 1, 1.0);
        Request newer = new Request(4, 2, 2.0);
        buffer.placeRequest(newer);
        buffer.placeRequest(older);
        buffer.placeRequest(new Request(1, 1, 3.0));

        assertSame(newer, buffer.findLowestPriorityRequest());
    }

    @Test
    void emptyBufferHasNoRefusalCandidate() {
        assertThrows(IllegalStateException.class, () -> new Buffer(3).findLowestPriorityRequest());
    }

    @Test
    void highestPrioritySourceIsTheSmallestSourceNumberPresent() {
        Buffer buffer = new Buffer(5);

        assertEquals(OptionalInt.empty(), buffer.highestPrioritySourcePresent());

        buffer.placeRequest(new Request(4, 1, 0.0));
        buffer.placeRequest(new Request(2, 1, 0.1));
        buffer.placeRequest(new Request(3, 1, 0.2));

        assertEquals(OptionalInt.of(2), buffer.highestPrioritySourcePresent());
    }

    @Test
    void requestsFromSourceListsThemInSlotOrderWithoutRemoving() {
        Buffer buffer = new Buffer(5);
        Request a = new Request(2, 1, 0.0);
        Request b = new Request(1, 1, 0.1);
        Request c = new Request(2, 2, 0.2);
        buffer.placeRequest(a);
        buffer.placeRequest(b);
        buffer.placeRequest(c);

        assertEquals(List.of(a, c), buffer.requestsFromSource(2));
        assertEquals(3, buffer.size());
    }
    @Test
    void removeAllFromSourceTakesThatSourcesRequestsAndFreesTheirSlots() {
        Buffer buffer = new Buffer(5);
        Request a = new Request(2, 1, 0.0);
        Request b = new Request(1, 1, 0.1);
        Request c = new Request(2, 2, 0.2);
        buffer.placeRequest(a);
        buffer.placeRequest(b);
        buffer.placeRequest(c);

        List<Request> removed = buffer.removeAllFromSource(2);

        assertEquals(List.of(a, c), removed);
        assertEquals(1, buffer.size());
        assertSame(b, buffer.snapshot().get(1));      // b did not move (Д1ОЗ3)
        assertNull(buffer.snapshot().get(0));
        assertTrue(buffer.removeAllFromSource(4).isEmpty());
    }
    @Test
    void removingAnAbsentRequestFails() {
        Buffer buffer = new Buffer(2);

        assertThrows(IllegalArgumentException.class, () -> buffer.remove(new Request(1, 1, 0.0)));
    }

    @Test
    void theSameRequestCannotBePlacedTwice() {
        Buffer buffer = new Buffer(2);
        Request r = new Request(1, 1, 0.0);
        buffer.placeRequest(r);

        assertThrows(IllegalArgumentException.class, () -> buffer.placeRequest(r));
    }

    @Test
    void snapshotDoesNotChangeWhenTheBufferChanges() {
        Buffer buffer = new Buffer(2);
        List<Request> before = buffer.snapshot();

        buffer.placeRequest(new Request(1, 1, 0.0));

        assertNull(before.get(0));
        assertThrows(UnsupportedOperationException.class, () -> before.set(0, null));
    }
}