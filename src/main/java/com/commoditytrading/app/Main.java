package com.commoditytrading.app;

import com.commoditytrading.simulation.SimulationEvent;
import com.commoditytrading.simulation.Simulator;

import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/**
 * Console entry point. Asks for the mode and the input parameters (Enter keeps the default), then
 * runs the simulation.
 *
 * <ul>
 *   <li><b>Step mode</b> (ОД3): one special event at a time, the event calendar after every step.
 *       Commands: Enter = next step, a number = that many steps, "r" = run to the end, "q" = quit.</li>
 *   <li><b>Automatic mode</b> (ОР1): runs to the end without stopping and prints only the summary.</li>
 * </ul>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        SimulationConfig d = SimulationConfig.defaults();

        System.out.println("СМО variant 24 — Commodity Trading and Supply Chain Platform");
        boolean automatic = askAutomatic(in);
        System.out.println("Press Enter to keep a default value.\n");
        SimulationConfig config = new SimulationConfig(
                askInt(in, "Number of sources", d.sourceCount()),
                askDouble(in, "ИЗ2: shortest interval a", d.minInterval()),
                askDouble(in, "ИЗ2: longest interval b", d.maxInterval()),
                askInt(in, "Number of devices", d.deviceCount()),
                askDouble(in, "ПЗ1: service rate λ", d.serviceRate()),
                askInt(in, "Buffer capacity", d.bufferCapacity()),
                askInt(in, "Number of requests N", d.requestLimit()),
                askLong(in, "Random seed", d.seed()));
        System.out.printf(Locale.ROOT, "%nSystem load ρ = %.3f%n", config.load());

        if (automatic) {
            runAutomatic(in, config);
        } else {
            runStepMode(in, config.buildSimulator());
        }
    }

    /** Automatic mode: optionally choose N by the formula, then the whole run at once and the summary. */
    private static void runAutomatic(Scanner in, SimulationConfig config) {
        String answer = ask(in, "Choose N by the formula? y = yes, n = use N as entered", "y");
        if (answer.equalsIgnoreCase("y")) {
            config = chooseRequestCount(config);
        }
        Simulator sim = config.buildSimulator();
        sim.run();
        System.out.printf(Locale.ROOT, "%nAutomatic mode: simulation complete at t = %.4f.%n",
                sim.getCurrentTime());
        System.out.print(SummaryView.render(sim));
    }

    /** Runs the N search, prints every run and returns the configuration with the chosen N. */
    private static SimulationConfig chooseRequestCount(SimulationConfig config) {
        System.out.printf(Locale.ROOT,
                "%nChoosing N = t²(1 − p)/(p·δ²), t = %.3f, δ = %.1f; stop when p changes by less than %.0f%%%n",
                RequestCountSelector.T_ALPHA, RequestCountSelector.DELTA, 100 * RequestCountSelector.STOP_CHANGE);
        System.out.printf(Locale.ROOT, "  %-4s %8s %8s%n", "Run", "N", "p_отк");
        List<RequestCountSelector.Run> runs = RequestCountSelector.select(config);
        for (int i = 0; i < runs.size(); i++) {
            RequestCountSelector.Run run = runs.get(i);
            System.out.printf(Locale.ROOT, "  %-4d %8d %7.1f%%%n",
                    i + 1, run.requestLimit(), 100 * run.refusalProbability());
        }
        int chosen = runs.get(runs.size() - 1).requestLimit();
        if (runs.size() == 1) {
            System.out.println("No change possible (p = 0 or 1), so N stays as entered.");
        }
        System.out.printf(Locale.ROOT, "Chosen N = %d%n", chosen);
        return config.withRequestLimit(chosen);
    }

    /** Step mode: the user decides how many events to process at a time. */
    private static void runStepMode(Scanner in, Simulator sim) {
        int step = 0;
        while (sim.hasNextEvent()) {
            System.out.print("\n[Enter] next step | number = that many steps | r = run to end | q = quit > ");
            if (!in.hasNextLine()) {
                break;
            }
            String command = in.nextLine().trim();
            if (command.equalsIgnoreCase("q")) {
                return;
            }
            int count = command.equalsIgnoreCase("r") ? Integer.MAX_VALUE
                    : command.isEmpty() ? 1 : parsePositive(command);
            for (int i = 0; i < count && sim.hasNextEvent(); i++) {
                List<SimulationEvent> events = sim.step();
                step++;
                System.out.print(StepModeView.render(step, events, sim));
            }
        }
        System.out.printf(Locale.ROOT, "%nSimulation complete after %d steps, at t = %.4f.%n",
                step, sim.getCurrentTime());
        System.out.print(SummaryView.render(sim));
    }

    /** Asks for the mode: "s" (or Enter) = step mode, "a" = automatic mode. */
    private static boolean askAutomatic(Scanner in) {
        while (true) {
            String answer = ask(in, "Mode: s = step, a = automatic", "s");
            if (answer.equalsIgnoreCase("s")) {
                return false;
            }
            if (answer.equalsIgnoreCase("a")) {
                return true;
            }
            System.out.println("  Please type s or a.");
        }
    }

    private static int parsePositive(String text) {
        try {
            return Math.max(1, Integer.parseInt(text));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static String ask(Scanner in, String label, String defaultValue) {
        System.out.print(label + " [" + defaultValue + "]: ");
        String line = in.hasNextLine() ? in.nextLine().trim() : "";
        return line.isEmpty() ? defaultValue : line;
    }

    private static int askInt(Scanner in, String label, int defaultValue) {
        while (true) {
            try {
                return Integer.parseInt(ask(in, label, String.valueOf(defaultValue)));
            } catch (NumberFormatException e) {
                System.out.println("  Please enter a whole number.");
            }
        }
    }

    private static long askLong(Scanner in, String label, long defaultValue) {
        while (true) {
            try {
                return Long.parseLong(ask(in, label, String.valueOf(defaultValue)));
            } catch (NumberFormatException e) {
                System.out.println("  Please enter a whole number.");
            }
        }
    }

    private static double askDouble(Scanner in, String label, double defaultValue) {
        while (true) {
            try {
                return Double.parseDouble(ask(in, label, String.valueOf(defaultValue)).replace(',', '.'));
            } catch (NumberFormatException e) {
                System.out.println("  Please enter a number, for example 0.5");
            }
        }
    }
}