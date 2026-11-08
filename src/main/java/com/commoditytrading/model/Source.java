package com.commoditytrading.model;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * An infinite source (ИБ) that generates requests with uniformly distributed intervals (ИЗ2).
 *
 * <p>"Infinite" means the source never waits for its earlier requests: after generating one
 * request it immediately draws the interval to the next one.
 *
 * <p>The source number is also its priority: source 1 has the highest priority and source 5 the
 * lowest. Д1ОО2 and Д2Б5 compare requests by this number.
 *
 * <p>The random generator is injected, so tests can pass a seeded generator and get the same
 * sequence of intervals on every run.
 */
public final class Source {

    private final int id;
    private final String name;
    private final double minInterval;
    private final double maxInterval;
    private final RandomGenerator random;

    private double nextArrivalTime;
    private int generatedCount;

    /**
     * @param id          source number, 1..n; also its priority (1 = highest)
     * @param name        business name, for example "Buyer System"
     * @param minInterval lower bound a of the uniform interval U(a, b), a >= 0
     * @param maxInterval upper bound b of the uniform interval U(a, b), b > a
     * @param random      random generator (seeded in tests)
     */
    public Source(int id, String name, double minInterval, double maxInterval, RandomGenerator random) {
        if (id < 1) {
            throw new IllegalArgumentException("id must be >= 1, got " + id);
        }
        if (minInterval < 0 || maxInterval <= minInterval) {
            throw new IllegalArgumentException(
                    "need 0 <= minInterval < maxInterval, got [" + minInterval + ", " + maxInterval + "]");
        }
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
        this.minInterval = minInterval;
        this.maxInterval = maxInterval;
        this.random = Objects.requireNonNull(random, "random");
        this.nextArrivalTime = drawInterval();   // the first request arrives after one interval
    }

    /**
     * Generates the request due at {@link #getNextArrivalTime()} and schedules the next one.
     *
     * <p>The simulator calls this only when the simulated clock has reached the next arrival time.
     */
    public Request generateNextRequest() {
        generatedCount++;
        Request request = new Request(id, generatedCount, nextArrivalTime);
        nextArrivalTime += drawInterval();
        return request;
    }

    /**
     * ИЗ2 — uniform interval on [a, b] by the inverse transform: a + (b − a) · U, where U ~ U[0, 1).
     */
    private double drawInterval() {
        return minInterval + (maxInterval - minInterval) * random.nextDouble();
    }

    /** Mean interval of U(a, b): (a + b) / 2. */
    public double getMeanInterval() {
        return (minInterval + maxInterval) / 2.0;
    }

    public int getId() {
        return id;
    }

    /** Same as the id: 1 is the highest priority. */
    public int getPriority() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getNextArrivalTime() {
        return nextArrivalTime;
    }

    public int getGeneratedCount() {
        return generatedCount;
    }

    @Override
    public String toString() {
        return "И" + id + " (" + name + ")";
    }
}