package com.commoditytrading.app;

import com.commoditytrading.model.Device;
import com.commoditytrading.model.Request;
import com.commoditytrading.model.Source;
import com.commoditytrading.simulation.SimulationEvent;
import com.commoditytrading.simulation.Simulator;
import com.commoditytrading.statistics.Statistics;

import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.stream.Collectors;

/**
 * Turns the simulator's state after one step into the text step mode prints: the events of the
 * step, the event calendar (each source's next arrival, each device's service end), the buffer
 * slot by slot, the Д2П2 pointer, the active Д2Б5 packet and the refusal rate.
 *
 * <p>It only reads the simulator; it never changes it.
 */
public final class StepModeView {

    private StepModeView() {
    }

    public static String render(int stepNumber, List<SimulationEvent> events, Simulator sim) {
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "%n========== Step %d   t = %.4f ==========%n",
                stepNumber, sim.getCurrentTime()));

        out.append("Events of this step:\n");
        for (SimulationEvent e : events) {
            out.append(String.format(Locale.ROOT, "  %-10.4f %-47s %-6s %s%n",
                    e.time(),
                    e.type().getDescription(),
                    e.request() == null ? "" : e.request().getLabel(),
                    e.deviceId() > 0 ? "П" + e.deviceId() : e.detail()));
        }

        Statistics stats = sim.getStatistics();
        boolean sourcesActive = stats.totalGenerated() < sim.getRequestLimit();
        out.append("\nEvent calendar:\n");
        out.append(String.format(Locale.ROOT, "  %-6s %-12s %-28s %9s %8s%n",
                "Object", "Next event", "State", "Requests", "Refused"));
        for (Source s : sim.getSources()) {
            out.append(String.format(Locale.ROOT, "  %-6s %-12s %-28s %9d %8d%n",
                    "И" + s.getId(),
                    sourcesActive ? time(s.getNextArrivalTime()) : "-",
                    sourcesActive ? "next request " + s.getId() + "." + (s.getGeneratedCount() + 1) : "stopped",
                    stats.generatedCount(s.getId()),
                    stats.refusedCount(s.getId())));
        }
        for (Device d : sim.getDevices()) {
            out.append(String.format(Locale.ROOT, "  %-6s %-12s %-28s%n",
                    "П" + d.getId(),
                    d.isFree() ? "-" : time(d.getServiceEndTime()),
                    d.isFree() ? "free" : "busy with " + d.getCurrentRequest().getLabel()));
        }

        out.append("\nBuffer (Д1ОЗ3, first free slot):\n");
        List<Request> slots = sim.getBufferSnapshot();
        for (int i = 0; i < slots.size(); i++) {
            Request r = slots.get(i);
            out.append(String.format(Locale.ROOT, "  [%2d] %-6s", i + 1, r == null ? "-" : r.getLabel()));
            if ((i + 1) % 5 == 0 || i == slots.size() - 1) {
                out.append('\n');
            }
        }

        out.append("\nД2П2 pointer: next search starts at П").append(sim.getPointerDeviceId()).append('\n');
        OptionalInt packet = sim.getActiveBatchSourceId();
        if (packet.isPresent()) {
            String waiting = sim.getPendingBatchMembers().stream()
                    .map(Request::getLabel)
                    .collect(Collectors.joining(", "));
            out.append("Д2Б5 packet: source ").append(packet.getAsInt())
                    .append(", still to hand out: [").append(waiting).append("]\n");
        } else {
            out.append("Д2Б5 packet: none active\n");
        }

        int generated = stats.totalGenerated();
        int refused = stats.totalRefused();
        out.append(String.format(Locale.ROOT, "Refused: %d of %d generated (%.1f%%)%n",
                refused, generated, generated == 0 ? 0.0 : 100.0 * refused / generated));
        return out.toString();
    }

    private static String time(double t) {
        return String.format(Locale.ROOT, "%.4f", t);
    }
}