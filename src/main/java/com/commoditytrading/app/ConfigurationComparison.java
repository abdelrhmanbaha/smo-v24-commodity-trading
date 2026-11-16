package com.commoditytrading.app;

import com.commoditytrading.model.Device;
import com.commoditytrading.simulation.Simulator;
import com.commoditytrading.statistics.Statistics;

import java.util.ArrayList;
import java.util.List;

/**
 * Compares several system configurations for the economic justification: for every combination of
 * device count, service rate λ and buffer size it chooses N by the formula, runs the simulation
 * and records the refusal probability, the mean device utilisation and the mean time in system.
 */
public final class ConfigurationComparison {

    /** The measured results of one configuration. */
    public record Result(SimulationConfig config, int chosenRequests, double refusalProbability,
                         double meanUtilization, double meanTimeInSystem) {
    }

    private ConfigurationComparison() {
    }

    /** Chooses N for the configuration, runs it with that N and measures the results. */
    public static Result evaluate(SimulationConfig config) {
        List<RequestCountSelector.Run> runs = RequestCountSelector.select(config);
        int chosen = runs.get(runs.size() - 1).requestLimit();
        Simulator sim = config.withRequestLimit(chosen).buildSimulator();
        sim.run();

        Statistics stats = sim.getStatistics();
        double totalTime = sim.getCurrentTime();
        double utilizationSum = 0;
        for (Device d : sim.getDevices()) {
            utilizationSum += Statistics.utilization(d, totalTime);
        }
        double timeSum = 0;
        int served = 0;
        for (int s = 1; s <= stats.sourceCount(); s++) {
            int n = stats.servedCount(s);
            if (n > 0) {
                timeSum += n * stats.meanTimeInSystem(s);
                served += n;
            }
        }
        return new Result(config, chosen, stats.overallRefusalProbability(),
                utilizationSum / sim.getDevices().size(),
                served == 0 ? Double.NaN : timeSum / served);
    }

    /** Evaluates every combination of the given device counts, service rates and buffer sizes. */
    public static List<Result> compare(SimulationConfig base, List<Integer> deviceCounts,
                                       List<Double> serviceRates, List<Integer> bufferCapacities) {
        List<Result> results = new ArrayList<>();
        for (int devices : deviceCounts) {
            for (double rate : serviceRates) {
                for (int buffer : bufferCapacities) {
                    results.add(evaluate(base.withSystem(devices, rate, buffer)));
                }
            }
        }
        return results;
    }

    /** Parses a comma-separated list of whole numbers, for example "2, 4, 6". */
    public static List<Integer> parseInts(String text) {
        List<Integer> values = new ArrayList<>();
        for (String part : text.split(",")) {
            if (!part.isBlank()) {
                values.add(Integer.parseInt(part.trim()));
            }
        }
        if (values.isEmpty()) {
            throw new NumberFormatException("empty list");
        }
        return values;
    }

    /** Parses a comma-separated list of numbers with a dot as decimal point, for example "0.6, 1.0". */
    public static List<Double> parseDoubles(String text) {
        List<Double> values = new ArrayList<>();
        for (String part : text.split(",")) {
            if (!part.isBlank()) {
                values.add(Double.parseDouble(part.trim()));
            }
        }
        if (values.isEmpty()) {
            throw new NumberFormatException("empty list");
        }
        return values;
    }
}