package com.commoditytrading.app;

import com.commoditytrading.simulation.SimulationEvent;
import com.commoditytrading.simulation.Simulator;

import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/**
 * Step mode: asks for the input parameters (Enter keeps the default), then runs the simulation
 * one special event at a time and prints the event calendar after every step.
 *
 * <p>Commands: Enter = next step, a number = that many steps, "r" = run to the end, "q" = quit.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        SimulationConfig d = SimulationConfig.defaults();

        System.out.println("СМО variant 24 — Commodity Trading and Supply Chain Platform");
        System.out.println("Step mode. Press Enter to keep a default value.\n");
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

        Simulator sim = config.buildSimulator();
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