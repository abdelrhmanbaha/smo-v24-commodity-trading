package com.commoditytrading.app;

import com.commoditytrading.model.Device;
import com.commoditytrading.model.Source;
import com.commoditytrading.simulation.Simulator;
import com.commoditytrading.statistics.Statistics;

import java.util.Locale;

/**
 * The summary printed at the end of a run (ОР1): one table per source and one per device.
 *
 * <p>Times and variances are taken over the requests that were served; a value that cannot be
 * computed (no served request, or fewer than two for a variance) is shown as "-".
 *
 * <p>It only reads the simulator; it never changes it.
 */
public final class SummaryView {

    private SummaryView() {
    }

    public static String render(Simulator sim) {
        Statistics stats = sim.getStatistics();
        double totalTime = sim.getCurrentTime();
        StringBuilder out = new StringBuilder();

        out.append(String.format(Locale.ROOT, "%n========== Summary (ОР1) ==========%n"));
        out.append(String.format(Locale.ROOT, "Total run time: %.4f%n", totalTime));
        out.append(String.format(Locale.ROOT, "Requests: %d generated, %d refused, %d served, p_отк = %s%n",
                stats.totalGenerated(), stats.totalRefused(), totalServed(stats),
                percent(stats.overallRefusalProbability())));

        out.append("\nSources:\n");
        out.append(String.format(Locale.ROOT, "  %-4s %6s %6s %6s %8s %8s %8s %8s %8s %8s%n",
                "", "N_gen", "N_ref", "N_srv", "p_отк", "T_ож", "T_обсл", "T_преб", "D_ож", "D_обсл"));
        for (Source s : sim.getSources()) {
            int id = s.getId();
            out.append(String.format(Locale.ROOT, "  %-4s %6d %6d %6d %8s %8s %8s %8s %8s %8s%n",
                    "И" + id,
                    stats.generatedCount(id),
                    stats.refusedCount(id),
                    stats.servedCount(id),
                    percent(stats.refusalProbability(id)),
                    number(stats.meanWaitingTime(id)),
                    number(stats.meanServiceTime(id)),
                    number(stats.meanTimeInSystem(id)),
                    number(stats.waitingTimeVariance(id)),
                    number(stats.serviceTimeVariance(id))));
        }

        out.append("\nDevices:\n");
        out.append(String.format(Locale.ROOT, "  %-4s %10s %8s%n", "", "Busy time", "K_исп"));
        for (Device d : sim.getDevices()) {
            double utilization = totalTime > 0 ? Statistics.utilization(d, totalTime) : Double.NaN;
            out.append(String.format(Locale.ROOT, "  %-4s %10.4f %8s%n",
                    "П" + d.getId(), d.getTotalBusyTime(), percent(utilization)));
        }
        return out.toString();
    }

    private static int totalServed(Statistics stats) {
        int served = 0;
        for (int s = 1; s <= stats.sourceCount(); s++) {
            served += stats.servedCount(s);
        }
        return served;
    }

    /** A probability as a percentage, or "-" if it is not defined. */
    private static String percent(double p) {
        return Double.isNaN(p) ? "-" : String.format(Locale.ROOT, "%.1f%%", 100.0 * p);
    }

    /** A time or variance with 4 decimals, or "-" if it is not defined. */
    private static String number(double x) {
        return Double.isNaN(x) ? "-" : String.format(Locale.ROOT, "%.4f", x);
    }
}