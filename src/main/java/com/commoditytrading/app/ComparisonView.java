package com.commoditytrading.app;

import java.util.List;
import java.util.Locale;

/**
 * Prints the configuration comparison as one table, one row per configuration. A row is marked
 * with "<-" when its refusal probability is within the target (for example 10%).
 */
public final class ComparisonView {

    private ComparisonView() {
    }

    public static String render(List<ConfigurationComparison.Result> results, double maxRefusal) {
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "%n========== Configuration comparison ==========%n"));
        out.append(String.format(Locale.ROOT, "Target: p_отк ≤ %.0f%%  (marked with <-)%n%n", 100 * maxRefusal));
        out.append(String.format(Locale.ROOT, "  %7s %6s %6s %6s %7s %8s %8s %9s%n",
                "Devices", "λ", "Buffer", "ρ", "N", "p_отк", "K_исп", "T_преб"));
        for (ConfigurationComparison.Result r : results) {
            SimulationConfig c = r.config();
            out.append(String.format(Locale.ROOT, "  %7d %6.2f %6d %6.2f %7d %7.1f%% %7.1f%% %9s%s%n",
                    c.deviceCount(), c.serviceRate(), c.bufferCapacity(), c.load(),
                    r.chosenRequests(), 100 * r.refusalProbability(), 100 * r.meanUtilization(),
                    Double.isNaN(r.meanTimeInSystem()) ? "-"
                            : String.format(Locale.ROOT, "%.4f", r.meanTimeInSystem()),
                    r.refusalProbability() <= maxRefusal ? "  <-" : ""));
        }
        out.append("\nK_исп = mean utilisation of the devices; T_преб = mean time in system of served requests.\n");
        return out.toString();
    }
}