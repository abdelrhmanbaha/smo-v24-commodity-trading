package com.commoditytrading.app;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestCountSelectorTest {

    @Test
    void formulaGivesTheExpectedNumberOfRequests() {
        // 1.643² · (1 − 0.4) / (0.4 · 0.1²) = 404.9 → 405
        assertEquals(405, RequestCountSelector.requiredRequests(0.4));
        // 1.643² · (1 − 0.5) / (0.5 · 0.1²) = 269.9 → 270
        assertEquals(270, RequestCountSelector.requiredRequests(0.5));
    }

    @Test
    void formulaRejectsAProbabilityOfZeroOrOne() {
        assertThrows(IllegalArgumentException.class, () -> RequestCountSelector.requiredRequests(0.0));
        assertThrows(IllegalArgumentException.class, () -> RequestCountSelector.requiredRequests(1.0));
    }

    @Test
    void searchStartsWithTheGivenNAndStopsWhenPIsStable() {
        List<RequestCountSelector.Run> runs = RequestCountSelector.select(SimulationConfig.defaults());

        assertEquals(100, runs.get(0).requestLimit());
        assertTrue(runs.size() >= 2 && runs.size() <= RequestCountSelector.MAX_RUNS);
        RequestCountSelector.Run before = runs.get(runs.size() - 2);
        RequestCountSelector.Run last = runs.get(runs.size() - 1);
        assertEquals(RequestCountSelector.requiredRequests(before.refusalProbability()), last.requestLimit());
        boolean stable = Math.abs(last.refusalProbability() - before.refusalProbability())
                < RequestCountSelector.STOP_CHANGE * before.refusalProbability();
        assertTrue(stable || runs.size() == RequestCountSelector.MAX_RUNS);
    }

    @Test
    void withoutRefusalsTheSearchStopsAtOnce() {
        SimulationConfig light = new SimulationConfig(5, 20.0, 30.0, 4, 1.0, 20, 200, 7);

        List<RequestCountSelector.Run> runs = RequestCountSelector.select(light);

        assertEquals(1, runs.size());
        assertEquals(200, runs.get(0).requestLimit());
        assertEquals(0.0, runs.get(0).refusalProbability());
    }
}