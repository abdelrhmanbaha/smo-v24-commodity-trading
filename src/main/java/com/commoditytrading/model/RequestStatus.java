package com.commoditytrading.model;

/**
 * Life-cycle of a request (заявка).
 *
 * <pre>
 * WAITING ──► IN_SERVICE ──► SERVED
 *    │
 *    └──────► REFUSED   (evicted from the buffer by Д1ОО2)
 * </pre>
 *
 * A request starts as WAITING the moment it is generated. If a device is free it moves to
 * IN_SERVICE at the same simulated instant; otherwise it waits in the buffer.
 */
public enum RequestStatus {
    WAITING,
    IN_SERVICE,
    SERVED,
    REFUSED
}