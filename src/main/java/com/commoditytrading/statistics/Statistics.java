package com.commoditytrading.statistics;

import com.commoditytrading.model.Device;
import com.commoditytrading.model.Request;
import com.commoditytrading.model.RequestStatus;

import java.util.Objects;

/**
 * Collects the statistics the course manual requires for each source:
 * the number of requests, the refusal probability, the mean time in system (T_преб), the mean
 * waiting time (T_БП), the mean service time (T_обсл) and the variances of T_БП and T_обсл.
 * Device utilization is computed from each device's busy time.
 *
 * <p>Times are accumulated over <b>served</b> requests. The simulation runs until the last
 * request leaves the system, so every request that started service is eventually counted.
 */
public final class Statistics {

    private final SourceCounters[] perSource;

    /** @param sourceCount number of sources; source ids are 1..sourceCount */
    public Statistics(int sourceCount) {
        if (sourceCount < 1) {
            throw new IllegalArgumentException("sourceCount must be >= 1, got " + sourceCount);
        }
        this.perSource = new SourceCounters[sourceCount];
        for (int i = 0; i < sourceCount; i++) {
            perSource[i] = new SourceCounters();
        }
    }

    /** A request was generated. */
    public void recordArrival(Request request) {
        countersFor(request).generated++;
    }

    /** A request started service on a device. */
    public void recordServiceStart(Request request) {
        requireStatus(request, RequestStatus.IN_SERVICE);
        countersFor(request).started++;
    }

    /** A request finished service. */
    public void recordServiceEnd(Request request) {
        requireStatus(request, RequestStatus.SERVED);
        SourceCounters c = countersFor(request);
        c.timeInSystem.add(request.getTimeInSystem());
        c.waitingTime.add(request.getWaitingTime());
        c.serviceTime.add(request.getServiceTime());
    }

    /** A request was refused (evicted from the buffer). */
    public void recordRefusal(Request request) {
        requireStatus(request, RequestStatus.REFUSED);
        countersFor(request).refused++;
    }

    public int generatedCount(int sourceId) {
        return counters(sourceId).generated;
    }

    public int refusedCount(int sourceId) {
        return counters(sourceId).refused;
    }

    public int startedCount(int sourceId) {
        return counters(sourceId).started;
    }

    public int servedCount(int sourceId) {
        return counters(sourceId).timeInSystem.count();
    }

    /** p_отк = refused / generated for one source; NaN if it generated nothing. */
    public double refusalProbability(int sourceId) {
        SourceCounters c = counters(sourceId);
        return c.generated == 0 ? Double.NaN : (double) c.refused / c.generated;
    }

    public double meanTimeInSystem(int sourceId) {
        return counters(sourceId).timeInSystem.mean();
    }

    public double meanWaitingTime(int sourceId) {
        return counters(sourceId).waitingTime.mean();
    }

    public double meanServiceTime(int sourceId) {
        return counters(sourceId).serviceTime.mean();
    }

    /** Д_БП — variance of the waiting time. */
    public double waitingTimeVariance(int sourceId) {
        return counters(sourceId).waitingTime.variance();
    }

    /** Д_обсл — variance of the service time. */
    public double serviceTimeVariance(int sourceId) {
        return counters(sourceId).serviceTime.variance();
    }

    public int totalGenerated() {
        int sum = 0;
        for (SourceCounters c : perSource) {
            sum += c.generated;
        }
        return sum;
    }

    public int totalRefused() {
        int sum = 0;
        for (SourceCounters c : perSource) {
            sum += c.refused;
        }
        return sum;
    }

    /** Refusal probability over all sources together; the p used to choose the number of requests N. */
    public double overallRefusalProbability() {
        int generated = totalGenerated();
        return generated == 0 ? Double.NaN : (double) totalRefused() / generated;
    }

    /** K_исп = total busy time of the device / total run time of the simulation. */
    public static double utilization(Device device, double totalRunTime) {
        Objects.requireNonNull(device, "device");
        if (totalRunTime <= 0) {
            throw new IllegalArgumentException("totalRunTime must be > 0, got " + totalRunTime);
        }
        return device.getTotalBusyTime() / totalRunTime;
    }

    public int sourceCount() {
        return perSource.length;
    }

    private SourceCounters countersFor(Request request) {
        Objects.requireNonNull(request, "request");
        return counters(request.getSourceId());
    }

    private SourceCounters counters(int sourceId) {
        if (sourceId < 1 || sourceId > perSource.length) {
            throw new IllegalArgumentException("Unknown source " + sourceId);
        }
        return perSource[sourceId - 1];
    }

    private static void requireStatus(Request request, RequestStatus expected) {
        Objects.requireNonNull(request, "request");
        if (request.getStatus() != expected) {
            throw new IllegalStateException("Expected " + expected + " but " + request);
        }
    }

    private static final class SourceCounters {
        private int generated;
        private int started;
        private int refused;
        private final RunningStats timeInSystem = new RunningStats();
        private final RunningStats waitingTime = new RunningStats();
        private final RunningStats serviceTime = new RunningStats();
    }

    /** Count, sum and sum of squares — enough for a mean and a sample variance. */
    private static final class RunningStats {
        private int n;
        private double sum;
        private double sumOfSquares;

        void add(double x) {
            n++;
            sum += x;
            sumOfSquares += x * x;
        }

        int count() {
            return n;
        }

        double mean() {
            return n == 0 ? Double.NaN : sum / n;
        }

        /** Sample variance with n − 1 in the denominator; NaN for fewer than two values. */
        double variance() {
            if (n < 2) {
                return Double.NaN;
            }
            double mean = sum / n;
            return Math.max(0.0, (sumOfSquares - n * mean * mean) / (n - 1));
        }
    }
}