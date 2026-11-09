package com.commoditytrading.app;

import com.commoditytrading.simulation.SimulationEvent;
import com.commoditytrading.simulation.Simulator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepModeTest {

    @Test
    void defaultsMatchTheStage1Structure() {
        SimulationConfig d = SimulationConfig.defaults();
        Simulator sim = d.buildSimulator();

        assertEquals(5, sim.getSources().size());
        assertEquals(4, sim.getDevices().size());
        assertEquals(20, sim.getBufferSnapshot().size());
        assertEquals("Buyer System", sim.getSources().get(0).getName());
        assertTrue(d.load() > 1.0, "the demo should be loaded enough to show refusals");
    }

    @Test
    void loadIsArrivalRateOverServiceRate() {
        // 5 sources, mean gap 2 → 2.5 requests per unit; 4 devices × 0.5 → 2.0 per unit
        SimulationConfig c = new SimulationConfig(5, 1.0, 3.0, 4, 0.5, 20, 100, 1);

        assertEquals(1.25, c.load(), 1e-12);
    }

    @Test
    void invalidParametersAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationConfig(5, 2.0, 1.0, 4, 0.5, 20, 100, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationConfig(0, 1.0, 2.0, 4, 0.5, 20, 100, 1));
    }

    @Test
    void stepViewShowsEventsCalendarBufferPointerAndRefusals() {
        Simulator sim = SimulationConfig.defaults().buildSimulator();
        List<SimulationEvent> events = sim.step();

        String text = StepModeView.render(1, events, sim);

        assertTrue(text.contains("Step 1"));
        assertTrue(text.contains("request generated"));
        assertTrue(text.contains("И1") && text.contains("И5"));
        assertTrue(text.contains("П1") && text.contains("П4"));
        assertTrue(text.contains("[20]"));
        assertTrue(text.contains("Д2П2 pointer"));
        assertTrue(text.contains("Д2Б5 packet"));
        assertTrue(text.contains("Refused: 0 of 1"));
    }

    @Test
    void aFullDemoRunFillsTheBufferAndShowsAPacket() {
        Simulator sim = SimulationConfig.defaults().buildSimulator();
        boolean sawPacket = false;
        boolean sawRefusal = false;
        int step = 0;
        while (sim.hasNextEvent()) {
            String text = StepModeView.render(++step, sim.step(), sim);
            sawPacket |= text.contains("packet formed");
            sawRefusal |= text.contains("refused: evicted");
        }

        assertTrue(sawPacket);
        assertTrue(sawRefusal);
    }
}