package com.commoditytrading.model;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * A device (прибор): one order/trade processing worker. Service time is exponential (ПЗ1).
 *
 * <p>Status changes in exactly two places:
 * <ul>
 *   <li>{@link #startService} — FREE → BUSY, at the moment a request is assigned;</li>
 *   <li>{@link #completeService} — BUSY → FREE, at the moment the service end time is reached.</li>
 * </ul>
 *
 * <p>Devices never refuse requests: they are only ever free or busy. All refusals happen in the
 * buffer.
 *
 * <p>The device keeps its own total busy time, which gives its utilization:
 * K_исп = total busy time / total run time.
 */
public final class Device {

    private final int id;
    private final double serviceRate;
    private final RandomGenerator random;

    private DeviceStatus status = DeviceStatus.FREE;
    private Request currentRequest;
    private double serviceEndTime = Double.NaN;
    private double totalBusyTime;

    /**
     * @param id          device number, 1..m
     * @param serviceRate λ of the exponential law, in requests per unit of time (mean service time = 1/λ)
     * @param random      random generator (seeded in tests)
     */
    public Device(int id, double serviceRate, RandomGenerator random) {
        if (id < 1) {
            throw new IllegalArgumentException("id must be >= 1, got " + id);
        }
        if (serviceRate <= 0) {
            throw new IllegalArgumentException("serviceRate must be > 0, got " + serviceRate);
        }
        this.id = id;
        this.serviceRate = serviceRate;
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * FREE → BUSY. Starts serving {@code request} at time {@code now}.
     *
     * @return the time at which service will end (now + an exponential service time)
     */
    public double startService(Request request, double now) {
        Objects.requireNonNull(request, "request");
        if (status != DeviceStatus.FREE) {
            throw new IllegalStateException("Device " + id + " is busy with " + currentRequest.getLabel());
        }
        request.markServiceStarted(now);
        double duration = drawServiceTime();
        this.currentRequest = request;
        this.serviceEndTime = now + duration;
        this.status = DeviceStatus.BUSY;
        return serviceEndTime;
    }

    /**
     * BUSY → FREE. Finishes the current request at its scheduled end time.
     *
     * @return the request that was just served
     */
    public Request completeService() {
        if (status != DeviceStatus.BUSY) {
            throw new IllegalStateException("Device " + id + " has nothing to complete");
        }
        Request finished = currentRequest;
        finished.markServed(serviceEndTime);
        totalBusyTime += finished.getServiceTime();
        this.currentRequest = null;
        this.status = DeviceStatus.FREE;
        this.serviceEndTime = Double.NaN;
        return finished;
    }

    /**
     * ПЗ1 — exponential service time by the inverse transform: −ln(1 − U) / λ, where U ~ U[0, 1).
     * Using 1 − U keeps the logarithm's argument in (0, 1], so it never becomes −∞.
     */
    private double drawServiceTime() {
        return -Math.log(1.0 - random.nextDouble()) / serviceRate;
    }

    public boolean isFree() {
        return status == DeviceStatus.FREE;
    }

    public int getId() {
        return id;
    }

    public DeviceStatus getStatus() {
        return status;
    }

    /** The request being served, or {@code null} when the device is free. */
    public Request getCurrentRequest() {
        return currentRequest;
    }

    /** When the current service ends, or NaN when the device is free. */
    public double getServiceEndTime() {
        return serviceEndTime;
    }

    public double getTotalBusyTime() {
        return totalBusyTime;
    }

    public double getServiceRate() {
        return serviceRate;
    }

    @Override
    public String toString() {
        return "П" + id + (isFree() ? " [free]" : " [busy: " + currentRequest.getLabel() + "]");
    }
}