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
    void withoutRefusalsNIsRaisedToTheCheckLimitAndTheSearchStops() {
        SimulationConfig light = new SimulationConfig(5, 20.0, 30.0, 4, 1.0, 20, 200, 7);

        List<RequestCountSelector.Run> runs = RequestCountSelector.select(light);

        assertEquals(List.of(200, 2000, 10_000), runs.stream().map(RequestCountSelector.Run::requestLimit).toList());
        assertEquals(0.0, runs.get(runs.size() - 1).refusalProbability());
    }

    @Test
    void aZeroAtSmallNIsCheckedWithALargerN() {
        // 4 devices with λ = 2.0: 100 requests end before the buffer fills (p = 0),
        // but a longer run does refuse requests, so the formula takes over.
        SimulationConfig fast = new SimulationConfig(5, 0.5, 2.5, 4, 2.0, 20, 100, 42);

        List<RequestCountSelector.Run> runs = RequestCountSelector.select(fast);

        assertEquals(0.0, runs.get(0).refusalProbability());
        assertTrue(runs.get(runs.size() - 1).refusalProbability() > 0);
    }
}