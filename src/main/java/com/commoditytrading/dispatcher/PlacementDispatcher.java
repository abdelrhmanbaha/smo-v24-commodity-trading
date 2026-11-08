package com.commoditytrading.dispatcher;

import com.commoditytrading.buffer.Buffer;
import com.commoditytrading.model.Request;
import com.commoditytrading.model.RequestStatus;

import java.util.Objects;
import java.util.Optional;

/**
 * Placement dispatcher (ДП): puts arriving requests into the buffer (Д1ОЗ3) and, when the buffer
 * is full, refuses one buffered request (Д1ОО2).
 *
 * <p>This is the classic design from the course memo: ДП works only with the buffer and knows
 * nothing about devices. The simulator calls it only for a request that could not go straight to
 * a device.
 *
 * <p>Trigger: the arrival of a request that was not served directly.
 */
public final class PlacementDispatcher {

    private final Buffer buffer;

    public PlacementDispatcher(Buffer buffer) {
        this.buffer = Objects.requireNonNull(buffer, "buffer");
    }

    /**
     * Places {@code request} into the buffer.
     *
     * <ul>
     *   <li>Free slot available → Д1ОЗ3: first free slot from the start. Nobody is refused.</li>
     *   <li>Buffer full → Д1ОО2: the lowest-priority request <b>already in the buffer</b> is
     *       refused and removed, then the new request takes the freed slot. The new request is
     *       never the one refused, even if its own priority is lower: Д1ОО2 chooses only among
     *       the requests in the buffer.</li>
     * </ul>
     *
     * @return the refused request, or empty if nobody was refused
     */
    public Optional<Request> onRequestArrival(Request request) {
        Objects.requireNonNull(request, "request");
        if (request.getStatus() != RequestStatus.WAITING) {
            throw new IllegalStateException("Only a waiting request can be placed: " + request);
        }

        if (!buffer.isFull()) {
            buffer.placeRequest(request);
            return Optional.empty();
        }

        Request refused = applyRefusalDiscipline(request.getGenerationTime());
        buffer.placeRequest(request);
        return Optional.of(refused);
    }

    /** Д1ОО2 — refuse the lowest-priority request in the buffer at time {@code now}. */
    private Request applyRefusalDiscipline(double now) {
        Request victim = buffer.findLowestPriorityRequest();
        buffer.remove(victim);
        victim.markRefused(now);
        return victim;
    }
}