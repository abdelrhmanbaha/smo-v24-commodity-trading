package com.commoditytrading.simulation;

/**
 * The kinds of events step mode shows, as listed in the course memo: request generated, sent to
 * the buffer or a device, evicted from the buffer, packet formed and request taken from it,
 * service finished.
 */
public enum EventType {
    GENERATED("request generated"),
    SENT_TO_DEVICE("sent straight to a device"),
    SENT_TO_BUFFER("placed in the buffer (Д1ОЗ3)"),
    REFUSED("refused: evicted from the full buffer (Д1ОО2)"),
    PACKET_FORMED("packet formed (Д2Б5)"),
    TAKEN_FROM_PACKET("taken from the packet to a device (Д2П2)"),
    SERVICE_FINISHED("service finished");

    private final String description;

    EventType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}