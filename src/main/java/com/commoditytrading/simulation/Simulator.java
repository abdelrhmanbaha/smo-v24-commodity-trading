package com.commoditytrading.simulation;

import com.commoditytrading.buffer.Buffer;
import com.commoditytrading.dispatcher.PlacementDispatcher;
import com.commoditytrading.dispatcher.SelectionDispatcher;
import com.commoditytrading.model.Device;
import com.commoditytrading.model.Request;
import com.commoditytrading.model.Source;
import com.commoditytrading.statistics.Statistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The discrete-event simulator: the clock and the event loop (метод особых событий).
 *
 * <p>Only two kinds of event change the system: a source generates a request, and a device
 * finishes one. Each {@link #step()} jumps the clock straight to the earliest of those, handles
 * it by calling the dispatchers, records statistics, and reports what happened.
 *
 * <p>Sources stop after {@code requestLimit} requests in total. The run then continues until the
 * last request has left the system; that moment is the total run time used for utilization.
 *
 * <p>If an arrival and a service end fall at exactly the same time, the service end is handled
 * first, so the freed device is available to the arrival.
 */
public final class Simulator {

    private final List<Source> sources;
    private final List<Device> devices;
    private final Buffer buffer;
    private final PlacementDispatcher placementDispatcher;
    private final SelectionDispatcher selectionDispatcher;
    private final Statistics statistics;
    private final int requestLimit;
    private final List<SimulationListener> listeners = new ArrayList<>();

    private double currentTime;

    /**
     * @param sources        sources numbered 1..n in this order (the number is the priority)
     * @param devices        devices numbered 1..m in this order
     * @param bufferCapacity number of buffer slots
     * @param requestLimit   total number of requests the sources generate before stopping (N)
     */
    public Simulator(List<Source> sources, List<Device> devices, int bufferCapacity, int requestLimit) {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(devices, "devices");
        if (sources.isEmpty() || devices.isEmpty()) {
            throw new IllegalArgumentException("at least one source and one device are required");
        }
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).getId() != i + 1) {
                throw new IllegalArgumentException("sources must be numbered 1..n in order");
            }
        }
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).getId() != i + 1) {
                throw new IllegalArgumentException("devices must be numbered 1..m in order");
            }
        }
        if (requestLimit < 1) {
            throw new IllegalArgumentException("requestLimit must be >= 1, got " + requestLimit);
        }
        this.sources = List.copyOf(sources);
        this.devices = List.copyOf(devices);
        this.buffer = new Buffer(bufferCapacity);
        this.placementDispatcher = new PlacementDispatcher(buffer);
        this.selectionDispatcher = new SelectionDispatcher(buffer, this.devices);
        this.statistics = new Statistics(sources.size());
        this.requestLimit = requestLimit;
    }

    public void addListener(SimulationListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** True while an event remains: a source still due to generate, or a device still busy. */
    public boolean hasNextEvent() {
        return nextArrivingSource() != null || nextFinishingDevice() != null;
    }

    /**
     * Handles the next special event and returns the events it produced, in order.
     *
     * @throws IllegalStateException if the run is already complete
     */
    public List<SimulationEvent> step() {
        Source source = nextArrivingSource();
        Device device = nextFinishingDevice();
        if (source == null && device == null) {
            throw new IllegalStateException("The simulation is complete");
        }
        double arrivalTime = source == null ? Double.POSITIVE_INFINITY : source.getNextArrivalTime();
        double endTime = device == null ? Double.POSITIVE_INFINITY : device.getServiceEndTime();

        List<SimulationEvent> events = new ArrayList<>();
        if (endTime <= arrivalTime) {
            handleServiceEnd(device, events);
        } else {
            handleArrival(source, events);
        }
        for (SimulationEvent event : events) {
            for (SimulationListener listener : listeners) {
                listener.onEvent(event);
            }
        }
        return events;
    }

    /** Runs to the end: until every request has been served or refused. */
    public void run() {
        while (hasNextEvent()) {
            step();
        }
    }

    /** Trigger 1: a source generates a request. */
    private void handleArrival(Source source, List<SimulationEvent> events) {
        currentTime = source.getNextArrivalTime();
        Request request = source.generateNextRequest();
        statistics.recordArrival(request);
        events.add(event(EventType.GENERATED, request, 0, ""));

        if (selectionDispatcher.tryServeDirectly(request)) {
            statistics.recordServiceStart(request);
            events.add(event(EventType.SENT_TO_DEVICE, request, deviceServing(request), ""));
            return;
        }

        Optional<Request> refused = placementDispatcher.onRequestArrival(request);
        refused.ifPresent(victim -> {
            statistics.recordRefusal(victim);
            events.add(event(EventType.REFUSED, victim, 0, ""));
        });
        events.add(event(EventType.SENT_TO_BUFFER, request, 0, ""));
    }

    /** Trigger 2: a device finishes its request. */
    private void handleServiceEnd(Device device, List<SimulationEvent> events) {
        currentTime = device.getServiceEndTime();
        Request finished = device.completeService();
        statistics.recordServiceEnd(finished);
        events.add(event(EventType.SERVICE_FINISHED, finished, device.getId(), ""));

        int packetsBefore = selectionDispatcher.getFormedBatchCount();
        List<Request> started = selectionDispatcher.onDeviceFree(device, currentTime);
        if (selectionDispatcher.getFormedBatchCount() > packetsBefore) {
            int size = started.size() + selectionDispatcher.getPendingBatchMembers().size();
            int sourceId = selectionDispatcher.getActiveBatchSourceId().orElseThrow();
            events.add(event(EventType.PACKET_FORMED, null, 0,
                    "source " + sourceId + ", " + size + " request(s)"));
        }
        for (Request request : started) {
            statistics.recordServiceStart(request);
            events.add(event(EventType.TAKEN_FROM_PACKET, request, deviceServing(request), ""));
        }
    }

    /** The source with the earliest next arrival, or null once the request limit is reached. */
    private Source nextArrivingSource() {
        if (statistics.totalGenerated() >= requestLimit) {
            return null;
        }
        Source earliest = null;
        for (Source s : sources) {
            if (earliest == null || s.getNextArrivalTime() < earliest.getNextArrivalTime()) {
                earliest = s;
            }
        }
        return earliest;
    }

    /** The busy device whose service ends first, or null if every device is free. */
    private Device nextFinishingDevice() {
        Device earliest = null;
        for (Device d : devices) {
            if (!d.isFree() && (earliest == null || d.getServiceEndTime() < earliest.getServiceEndTime())) {
                earliest = d;
            }
        }
        return earliest;
    }

    private int deviceServing(Request request) {
        for (Device d : devices) {
            if (d.getCurrentRequest() == request) {
                return d.getId();
            }
        }
        throw new IllegalStateException("No device is serving " + request);
    }

    private SimulationEvent event(EventType type, Request request, int deviceId, String detail) {
        return new SimulationEvent(currentTime, type, request, deviceId, detail);
    }

    // ---- read-only views for step mode and automatic mode ----

    public double getCurrentTime() {
        return currentTime;
    }

    public int getRequestLimit() {
        return requestLimit;
    }

    public Statistics getStatistics() {
        return statistics;
    }

    public List<Source> getSources() {
        return sources;
    }

    public List<Device> getDevices() {
        return devices;
    }

    public List<Request> getBufferSnapshot() {
        return buffer.snapshot();
    }

    public int getPointerDeviceId() {
        return selectionDispatcher.getPointerDeviceId();
    }

    public OptionalInt getActiveBatchSourceId() {
        return selectionDispatcher.getActiveBatchSourceId();
    }

    public List<Request> getPendingBatchMembers() {
        return selectionDispatcher.getPendingBatchMembers();
    }
}