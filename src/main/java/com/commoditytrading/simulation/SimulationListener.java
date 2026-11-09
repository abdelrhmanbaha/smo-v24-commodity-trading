package com.commoditytrading.simulation;

/**
 * Receives every event the simulator produces. Step mode is one listener; a future adapter that
 * publishes events to other services (the microservice version) can be another, without any
 * change to the simulation core.
 */
@FunctionalInterface
public interface SimulationListener {

    void onEvent(SimulationEvent event);
}