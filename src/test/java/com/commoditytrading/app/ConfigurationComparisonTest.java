package com.commoditytrading.app;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationComparisonTest {

    @Test
    void evaluatingTheDefaultsUsesTheNChosenByTheFormula() {
        ConfigurationComparison.Result r = ConfigurationComparison.evaluate(SimulationConfig.defaults());

        List<RequestCountSelector.Run> runs = RequestCountSelector.select(SimulationConfig.defaults());
        RequestCountSelector.Run last = runs.get(runs.size() - 1);
        assertEquals(last.requestLimit(), r.chosenRequests());
        assertEquals(last.refusalProbability(), r.refusalProbability());
        assertTrue(r.meanUtilization() > 0 && r.meanUtilization() <= 1);
        assertTrue(r.meanTimeInSystem() > 0);
    }

    @Test
    void everyCombinationIsEvaluatedInOrder() {
        List<ConfigurationComparison.Result> results = ConfigurationComparison.compare(
                SimulationConfig.defaults(), List.of(2, 4), List.of(0.6, 2.0), List.of(10, 20));

        assertEquals(8, results.size());
        SimulationConfig first = results.get(0).config();
        SimulationConfig last = results.get(7).config();
        assertEquals(2, first.deviceCount());
        assertEquals(0.6, first.serviceRate());
        assertEquals(10, first.bufferCapacity());
        assertEquals(4, last.deviceCount());
        assertEquals(2.0, last.serviceRate());
        assertEquals(20, last.bufferCapacity());
    }

    @Test
    void fasterDevicesRefuseLess() {
        List<ConfigurationComparison.Result> results = ConfigurationComparison.compare(
                SimulationConfig.defaults(), List.of(4), List.of(0.6, 3.0), List.of(20));

        assertTrue(results.get(1).refusalProbability() < results.get(0).refusalProbability());
    }

    @Test
    void listsAreParsedFromCommaSeparatedText() {
        assertEquals(List.of(2, 4, 6), ConfigurationComparison.parseInts("2, 4,6"));
        assertEquals(List.of(0.6, 1.5), ConfigurationComparison.parseDoubles("0.6, 1.5"));
        assertThrows(NumberFormatException.class, () -> ConfigurationComparison.parseInts(" , "));
        assertThrows(NumberFormatException.class, () -> ConfigurationComparison.parseInts("2, x"));
    }
}