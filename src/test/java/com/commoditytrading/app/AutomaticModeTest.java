package com.commoditytrading.app;

import com.commoditytrading.simulation.Simulator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutomaticModeTest {

    @Test
    void automaticAndStepModeGiveTheSameResult() {
        Simulator automatic = SimulationConfig.defaults().buildSimulator();
        automatic.run();

        Simulator stepByStep = SimulationConfig.defaults().buildSimulator();
        while (stepByStep.hasNextEvent()) {
            stepByStep.step();
        }

        assertEquals(SummaryView.render(stepByStep), SummaryView.render(automatic));
    }

    @Test
    void theSameConfigurationAlwaysGivesTheSameSummary() {
        Simulator first = new SimulationConfig(5, 0.5, 2.5, 6, 0.6, 20, 500, 1).buildSimulator();
        Simulator second = new SimulationConfig(5, 0.5, 2.5, 6, 0.6, 20, 500, 1).buildSimulator();

        first.run();
        second.run();

        assertEquals(SummaryView.render(first), SummaryView.render(second));
    }
}