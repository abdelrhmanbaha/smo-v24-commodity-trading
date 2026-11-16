package com.commoditytrading.app;

import com.commoditytrading.simulation.Simulator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummaryViewTest {

    @Test
    void summaryShowsEverySourceAndDeviceAndTheTotals() {
        Simulator sim = SimulationConfig.defaults().buildSimulator();
        sim.run();

        String text = SummaryView.render(sim);

        assertTrue(text.contains("Summary (ОР1)"));
        assertTrue(text.contains("И1") && text.contains("И5"));
        assertTrue(text.contains("П1") && text.contains("П4"));
        assertTrue(text.contains("100 generated, 40 refused, 60 served, p_отк = 40.0%"));
        assertTrue(text.contains("K_исп"));
    }

    @Test
    void aLightlyLoadedRunHasNoRefusals() {
        Simulator sim = new SimulationConfig(5, 20.0, 30.0, 4, 1.0, 20, 200, 7).buildSimulator();
        sim.run();

        String text = SummaryView.render(sim);

        assertTrue(text.contains("200 generated, 0 refused, 200 served, p_отк = 0.0%"));
        assertFalse(text.contains("NaN"));
    }
}