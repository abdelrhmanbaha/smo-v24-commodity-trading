package com.commoditytrading.buffer;

import com.commoditytrading.model.Request;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Common (non-zoned) buffer memory (БП) with a fixed number of slots.
 *
 * <p>Placement follows Д1ОЗ3: a request takes the <b>first free slot from the start</b>, and the
 * other requests are never shifted. A slot freed in the middle is therefore reused before any
 * slot after it.
 *
 * <p>The buffer only stores requests and answers questions about them. It never decides who is
 * refused or who is served next — that is the dispatchers' job.
 */
public final class Buffer {

    private final Request[] slots;

    public Buffer(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1, got " + capacity);
        }
        this.slots = new Request[capacity];
    }

    /**
     * Д1ОЗ3 — puts the request into the first free slot, counting from slot 0. No shifting.
     *
     * @return {@code true} if placed, {@code false} if the buffer is full
     */
    public boolean placeRequest(Request request) {
        Objects.requireNonNull(request, "request");
        if (contains(request)) {
            throw new IllegalArgumentException("Request " + request.getLabel() + " is already in the buffer");
        }
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) {
                slots[i] = request;
                return true;
            }
        }
        return false;
    }

    /** Removes the request from its slot; the slot becomes free and nothing moves. */
    public void remove(Request request) {
        Objects.requireNonNull(request, "request");
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == request) {
                slots[i] = null;
                return;
            }
        }
        throw new IllegalArgumentException("Request " + request.getLabel() + " is not in the buffer");
    }

    /**
     * The candidate for refusal under Д1ОО2: the request with the <b>lowest priority</b>, that is
     * the <b>largest source number</b>, among the requests currently in the buffer.
     *
     * <p>Tie-break (our design decision): if several requests share that source,
     * the <b>newest</b> one (latest generation time) is chosen — it has waited the least.
     *
     * @throws IllegalStateException if the buffer is empty
     */
    public Request findLowestPriorityRequest() {
        Request candidate = null;
        for (Request r : slots) {
            if (r == null) {
                continue;
            }
            if (candidate == null
                    || r.getSourceId() > candidate.getSourceId()
                    || (r.getSourceId() == candidate.getSourceId()
                    && r.getGenerationTime() > candidate.getGenerationTime())) {
                candidate = r;
            }
        }
        if (candidate == null) {
            throw new IllegalStateException("The buffer is empty: there is nothing to refuse");
        }
        return candidate;
    }

    /**
     * The highest-priority source (smallest source number) that has at least one request in the
     * buffer, or empty if the buffer is empty. Д2Б5 uses this to choose which source's packet to
     * serve.
     */
    public OptionalInt highestPrioritySourcePresent() {
        return Arrays.stream(slots)
                .filter(Objects::nonNull)
                .mapToInt(Request::getSourceId)
                .min();
    }

    /** All requests of one source currently in the buffer, in slot order. Nothing is removed. */
    public List<Request> requestsFromSource(int sourceId) {
        List<Request> result = new ArrayList<>();
        for (Request r : slots) {
            if (r != null && r.getSourceId() == sourceId) {
                result.add(r);
            }
        }
        return result;
    }
    /**
     * Removes and returns all requests of one source, in slot order. Д2Б5 uses this to form a
     * packet: at that moment the packet's members leave the buffer and their slots become free.
     */
    public List<Request> removeAllFromSource(int sourceId) {
        List<Request> removed = new ArrayList<>();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null && slots[i].getSourceId() == sourceId) {
                removed.add(slots[i]);
                slots[i] = null;
            }
        }
        return removed;
    }
    public boolean contains(Request request) {
        for (Request r : slots) {
            if (r == request) {
                return true;
            }
        }
        return false;
    }

    public boolean isFull() {
        return size() == slots.length;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public int size() {
        int count = 0;
        for (Request r : slots) {
            if (r != null) {
                count++;
            }
        }
        return count;
    }

    public int capacity() {
        return slots.length;
    }

    /**
     * A read-only copy of the slots, slot 0 first; a free slot is {@code null}. Used by step mode
     * to display the buffer. Changing the buffer afterwards does not change this list.
     */
    public List<Request> snapshot() {
        return Collections.unmodifiableList(Arrays.asList(slots.clone()));
    }
}