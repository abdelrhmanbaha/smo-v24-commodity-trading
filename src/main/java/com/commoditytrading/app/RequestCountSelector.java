package com.commoditytrading.app;

import com.commoditytrading.simulation.Simulator;

import java.util.ArrayList;
import java.util.List;

/**
 * Chooses the number of requests N for automatic mode, as the course manual describes.
 *
 * <ol>
 *   <li>Run with the starting N (for example 100) and measure the refusal probability p.</li>
 *   <li>Compute N = t_α² · (1 − p) / (p · δ²) with t_α = 1.643 (α = 0.9) and δ = 0.1.</li>
 *   <li>Run again with that N and measure the new p.</li>
 *   <li>Stop when p changed by less than 10%; otherwise repeat from step 2 with the new p.</li>
 * </ol>
 *
 * <p>If no request is refused (p = 0) the formula is undefined, so the search stops at once.
 */
public final class RequestCountSelector {

    /** Student coefficient for confidence level α = 0.9. */
    public static final double T_ALPHA = 1.643;
    /** Relative accuracy of the refusal probability. */
    public static final double DELTA = 0.1;
    /** Stop when p changes by less than this share of its previous value. */
    public static final double STOP_CHANGE = 0.1;
    /** Upper limit for N, so a tiny p cannot make a run endless. */
    public static final int MAX_REQUESTS = 1_000_000;
    /** Upper limit for the number of runs. */
    public static final int MAX_RUNS = 10;

    /** One run of the search: the N used and the refusal probability measured. */
    public record Run(int requestLimit, double refusalProbability) {
    }

    private RequestCountSelector() {
    }

    /** N = t_α² · (1 − p) / (p · δ²), rounded up and kept between 1 and {@link #MAX_REQUESTS}. */
    public static int requiredRequests(double p) {
        if (p <= 0 || p >= 1) {
            throw new IllegalArgumentException("p must be between 0 and 1 (exclusive), got " + p);
        }
        double n = T_ALPHA * T_ALPHA * (1 - p) / (p * DELTA * DELTA);
        return (int) Math.min(MAX_REQUESTS, Math.max(1, Math.ceil(n)));
    }

    /** Runs the search from the given configuration; the last run in the list holds the chosen N. */
    public static List<Run> select(SimulationConfig start) {
        List<Run> runs = new ArrayList<>();
        SimulationConfig config = start;
        double p = refusalProbability(config);
        runs.add(new Run(config.requestLimit(), p));

        while (runs.size() < MAX_RUNS && p > 0 && p < 1) {
            config = config.withRequestLimit(requiredRequests(p));
            double next = refusalProbability(config);
            runs.add(new Run(config.requestLimit(), next));
            boolean stable = Math.abs(next - p) < STOP_CHANGE * p;
            p = next;
            if (stable) {
                break;
            }
        }
        return runs;
    }

    private static double refusalProbability(SimulationConfig config) {
        Simulator sim = config.buildSimulator();
        sim.run();
        return sim.getStatistics().overallRefusalProbability();
    }
}