package com.commoditytrading.simulation;

import com.commoditytrading.model.Request;

import java.util.Objects;

/**
 * One thing that happened in the simulation, for step mode and for any other listener.
 *
 * @param time     simulated time of the event
 * @param type     what happened
 * @param request  the request involved, or {@code null} for an event about a packet as a whole
 * @param deviceId the device involved (1..m), or 0 if none
 * @param detail   extra text, for example the size of a new packet; empty if none
 */
public record SimulationEvent(double time, EventType type, Request request, int deviceId, String detail) {

    public SimulationEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(detail, "detail");
    }
}