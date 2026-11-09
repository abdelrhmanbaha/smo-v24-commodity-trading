package com.commoditytrading.app;

import com.commoditytrading.model.Device;
import com.commoditytrading.model.Source;
import com.commoditytrading.simulation.Simulator;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * The input parameters of one simulation run: everything the course manual lets you vary
 * (sources and their intervals, devices and their speed, buffer size, number of requests).
 *
 * <p>All sources share the same uniform interval U(a, b) and all devices the same rate λ. The
 * five source names come from the Stage 1 business domain.
 *
 * @param sourceCount    number of sources (5 in the Stage 1 mapping)
 * @param minInterval    a of ИЗ2, the shortest gap between two requests of one source
 * @param maxInterval    b of ИЗ2, the longest gap
 * @param deviceCount    number of devices (4 in the Stage 1 mapping)
 * @param serviceRate    λ of ПЗ1 for every device; mean service time = 1/λ
 * @param bufferCapacity number of buffer slots (20 in the Stage 1 mapping)
 * @param requestLimit   N, the total number of requests the sources generate
 * @param seed           seed of the random generators; the same seed repeats the same run
 */
public record SimulationConfig(int sourceCount, double minInterval, double maxInterval,
                               int deviceCount, double serviceRate,
                               int bufferCapacity, int requestLimit, long seed) {

    /** The five business channels of the Stage 1 domain, in priority order. */
    public static final List<String> SOURCE_NAMES = List.of(
            "Buyer System",
            "Supplier System",
            "Logistics System",
            "Warehouse/Inventory System",
            "Internal Trading System");

    public SimulationConfig {
        if (sourceCount < 1 || deviceCount < 1 || bufferCapacity < 1 || requestLimit < 1) {
            throw new IllegalArgumentException("counts must be >= 1");
        }
        if (minInterval < 0 || maxInterval <= minInterval) {
            throw new IllegalArgumentException("need 0 <= a < b");
        }
        if (serviceRate <= 0) {
            throw new IllegalArgumentException("serviceRate must be > 0");
        }
    }

    /**
     * Defaults for a step-mode demonstration: the Stage 1 structure (5 sources, 4 devices,
     * 20 slots) with a load slightly above 1, so the buffer fills and refusals appear.
     */
    public static SimulationConfig defaults() {
        return new SimulationConfig(5, 0.5, 2.5, 4, 0.6, 20, 100, 42);
    }

    /** Builds a fresh simulator from these parameters. */
    public Simulator buildSimulator() {
        SplittableRandom root = new SplittableRandom(seed);
        List<Source> sources = new ArrayList<>();
        for (int i = 1; i <= sourceCount; i++) {
            String name = i <= SOURCE_NAMES.size() ? SOURCE_NAMES.get(i - 1) : "Source " + i;
            sources.add(new Source(i, name, minInterval, maxInterval, root.split()));
        }
        List<Device> devices = new ArrayList<>();
        for (int i = 1; i <= deviceCount; i++) {
            devices.add(new Device(i, serviceRate, root.split()));
        }
        return new Simulator(sources, devices, bufferCapacity, requestLimit);
    }

    /**
     * System load ρ = total arrival rate / total service rate. One source sends on average
     * 1 / ((a + b) / 2) requests per unit of time; one device serves λ.
     */
    public double load() {
        double arrivalRate = sourceCount / ((minInterval + maxInterval) / 2.0);
        return arrivalRate / (deviceCount * serviceRate);
    }
}