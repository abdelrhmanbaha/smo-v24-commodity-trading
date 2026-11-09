package com.commoditytrading.simulation;

import com.commoditytrading.model.Device;
import com.commoditytrading.model.Request;
import com.commoditytrading.model.Source;
import com.commoditytrading.statistics.Statistics;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatorTest {

    /** 5 sources with intervals U(a, b), 4 devices with rate lambda, a 20-slot buffer. */
    private static Simulator simulator(double a, double b, double lambda, int requestLimit, long seed) {
        SplittableRandom root = new SplittableRandom(seed);
        List<Source> sources = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            sources.add(new Source(i, "Source " + i, a, b, root.split()));
        }
        List<Device> devices = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            devices.add(new Device(i, lambda, root.split()));
        }
        return new Simulator(sources, devices, 20, requestLimit);
    }

    private static int totalServed(Statistics stats) {
        int served = 0;
        for (int s = 1; s <= stats.sourceCount(); s++) {
            served += stats.servedCount(s);
        }
        return served;
    }

    @Test
    void firstStepIsTheEarliestArrivalAndGoesStraightToDevice1() {
        Simulator sim = simulator(1.0, 2.0, 1.0, 100, 1);
        double earliest = sim.getSources().stream().mapToDouble(Source::getNextArrivalTime).min().orElseThrow();

        List<SimulationEvent> events = sim.step();

        assertEquals(EventType.GENERATED, events.get(0).type());
        assertEquals(EventType.SENT_TO_DEVICE, events.get(1).type());
        assertEquals(1, events.get(1).deviceId());
        assertEquals(earliest, sim.getCurrentTime());
        assertEquals(2, sim.getPointerDeviceId());
    }

    @Test
    void everyRequestIsEitherServedOrRefusedAndTheSystemEndsEmpty() {
        Simulator sim = simulator(0.2, 0.8, 1.0, 5_000, 2);

        sim.run();

        Statistics stats = sim.getStatistics();
        assertEquals(5_000, stats.totalGenerated());
        assertEquals(5_000, totalServed(stats) + stats.totalRefused());
        assertTrue(sim.getBufferSnapshot().stream().allMatch(Objects::isNull));
        assertTrue(sim.getDevices().stream().allMatch(Device::isFree));
        assertTrue(sim.getPendingBatchMembers().isEmpty());
        assertFalse(sim.hasNextEvent());
    }

    @Test
    void clockNeverRunsBackwards() {
        Simulator sim = simulator(0.2, 0.8, 1.0, 3_000, 3);

        double previous = 0.0;
        while (sim.hasNextEvent()) {
            sim.step();
            assertTrue(sim.getCurrentTime() >= previous);
            previous = sim.getCurrentTime();
        }
    }

    @Test
    void invariantsHoldAfterEveryEvent() {
        Simulator sim = simulator(0.05, 0.3, 1.0, 20_000, 4);   // heavily overloaded

        while (sim.hasNextEvent()) {
            sim.step();
            boolean someDeviceFree = sim.getDevices().stream().anyMatch(Device::isFree);
            boolean bufferEmpty = sim.getBufferSnapshot().stream().allMatch(Objects::isNull);
            // Invariant I: no device is idle while packet members wait to be handed out
            assertFalse(!sim.getPendingBatchMembers().isEmpty() && someDeviceFree, "Invariant I");
            // Invariant II: no device is idle while no packet is active and requests wait
            assertFalse(sim.getActiveBatchSourceId().isEmpty() && !bufferEmpty && someDeviceFree,
                    "Invariant II");
        }
    }

    @Test
    void anOverloadedSystemRefusesAndALightlyLoadedOneDoesNot() {
        Simulator overloaded = simulator(0.05, 0.3, 1.0, 5_000, 5);
        Simulator light = simulator(20.0, 30.0, 1.0, 5_000, 5);

        overloaded.run();
        light.run();

        assertTrue(overloaded.getStatistics().totalRefused() > 0);
        assertEquals(0, light.getStatistics().totalRefused());
    }

    @Test
    void theSameSeedGivesTheSameRun() {
        Simulator a = simulator(0.2, 0.8, 1.0, 2_000, 6);
        Simulator b = simulator(0.2, 0.8, 1.0, 2_000, 6);

        a.run();
        b.run();

        assertEquals(a.getCurrentTime(), b.getCurrentTime());
        assertEquals(a.getStatistics().totalRefused(), b.getStatistics().totalRefused());
    }

    @Test
    void utilizationIsBetweenZeroAndOne() {
        Simulator sim = simulator(0.2, 0.8, 1.0, 5_000, 7);

        sim.run();

        for (Device d : sim.getDevices()) {
            double k = Statistics.utilization(d, sim.getCurrentTime());
            assertTrue(k > 0.0 && k <= 1.0, "utilization of " + d + " = " + k);
        }
    }

    @Test
    void listenersReceiveTheSameEventsAsStep() {
        Simulator sim = simulator(1.0, 2.0, 1.0, 50, 8);
        List<SimulationEvent> heard = new ArrayList<>();
        sim.addListener(heard::add);

        List<SimulationEvent> returned = new ArrayList<>();
        while (sim.hasNextEvent()) {
            returned.addAll(sim.step());
        }

        assertEquals(returned, heard);
    }

    @Test
    void steppingAfterTheEndFails() {
        Simulator sim = simulator(1.0, 2.0, 1.0, 10, 9);
        sim.run();

        assertThrows(IllegalStateException.class, sim::step);
    }

    @Test
    void packetMembersAreReportedWhenTheyStartService() {
        Simulator sim = simulator(0.05, 0.3, 1.0, 500, 10);
        List<SimulationEvent> events = new ArrayList<>();
        sim.addListener(events::add);

        sim.run();

        long formed = events.stream().filter(e -> e.type() == EventType.PACKET_FORMED).count();
        long taken = events.stream().filter(e -> e.type() == EventType.TAKEN_FROM_PACKET).count();
        assertTrue(formed > 0);
        assertTrue(taken >= formed);
        for (SimulationEvent e : events) {
            if (e.type() == EventType.TAKEN_FROM_PACKET) {
                Request r = e.request();
                assertTrue(e.deviceId() >= 1 && e.deviceId() <= 4);
                assertTrue(r.getServiceStartTime() >= r.getGenerationTime());
            }
        }
    }
}