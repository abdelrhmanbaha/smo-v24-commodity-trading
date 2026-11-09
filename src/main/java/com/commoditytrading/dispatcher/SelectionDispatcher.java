package com.commoditytrading.dispatcher;

import com.commoditytrading.buffer.Buffer;
import com.commoditytrading.model.Device;
import com.commoditytrading.model.Request;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Selection dispatcher (ДВ): chooses <b>which device</b> serves (Д2П2, round-robin) and
 * <b>which waiting requests</b> are served (Д2Б5, packets by source priority).
 *
 * <p><b>Д2П2.</b> A pointer remembers where the last device search stopped. Each search starts at
 * the pointer, goes round the devices in order, takes the first free one, and moves the pointer to
 * the device after it.
 *
 * <p><b>Д2Б5</b>, as defined in the course memo:
 * <ul>
 *   <li>A packet is formed when a device frees and no packet is active. It contains <b>all</b>
 *       requests in the buffer from the highest-priority source present; they leave the buffer at
 *       that moment and are served oldest first.</li>
 *   <li>The packet is frozen: requests of that source that arrive later do not join it.</li>
 *   <li>All devices serve only that packet. Every device that is free gets the next member.</li>
 *   <li>A new packet is formed only after the current one is <b>fully processed</b>: no member
 *       left to hand out and no device still serving a member.</li>
 * </ul>
 *
 * <p>Triggers: the arrival of a request ({@link #tryServeDirectly}) and the release of a device
 * ({@link #onDeviceFree}). ДВ does nothing on its own between those events.
 */
public final class SelectionDispatcher {

    private final Buffer buffer;
    private final Device[] devices;
    private int devicePointer;

    private final Deque<Request> currentBatch = new ArrayDeque<>();
    private int currentBatchSourceId;
    private final Set<Device> batchDevices = new LinkedHashSet<>();
    private int formedBatchCount;
    /**
     * @param buffer  the buffer shared with the placement dispatcher
     * @param devices the devices in pointer order (device 1 first)
     */
    public SelectionDispatcher(Buffer buffer, List<Device> devices) {
        this.buffer = Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(devices, "devices");
        if (devices.isEmpty()) {
            throw new IllegalArgumentException("at least one device is required");
        }
        this.devices = devices.toArray(new Device[0]);
    }

    /**
     * Trigger: a request has just arrived. Serves it immediately if <b>no packet is active and a
     * device is free</b> (Д2П2 chooses the device). A free device never takes a new arrival while
     * a packet is active: the arrival must not overtake the packet.
     *
     * @return {@code true} if the request went straight to a device, {@code false} if it must be
     *         placed in the buffer by the placement dispatcher
     */
    public boolean tryServeDirectly(Request request) {
        Objects.requireNonNull(request, "request");
        if (isBatchActive()) {
            return false;
        }
        Device device = selectFreeDevice();
        if (device == null) {
            return false;
        }
        device.startService(request, request.getGenerationTime());
        return true;
    }

    /**
     * Trigger: {@code device} has just finished its request at time {@code now}.
     *
     * <ol>
     *   <li>If the device was serving a packet member, it leaves the packet's device set.</li>
     *   <li>If the packet is now fully processed (or none was active), a new packet is formed.</li>
     *   <li>Every free device gets the next packet member (Д2П2 chooses the order).</li>
     * </ol>
     *
     * @return the requests that started service during this call, possibly none
     */
    public List<Request> onDeviceFree(Device device, double now) {
        Objects.requireNonNull(device, "device");
        if (!device.isFree()) {
            throw new IllegalStateException(device + " is not free");
        }
        batchDevices.remove(device);
        if (!isBatchActive()) {
            formNewBatch();
        }
        return dispatchBatch(now);
    }

    /** A packet is active while it has members to hand out or devices still serving them. */
    private boolean isBatchActive() {
        return !currentBatch.isEmpty() || !batchDevices.isEmpty();
    }

    /**
     * Д2Б5 — takes every buffered request of the highest-priority source present, oldest first.
     * Does nothing if the buffer is empty.
     */
    private void formNewBatch() {
        OptionalInt source = buffer.highestPrioritySourcePresent();
        if (source.isEmpty()) {
            currentBatchSourceId = 0;
            return;
        }
        List<Request> members = buffer.removeAllFromSource(source.getAsInt());
        members.sort(Comparator.comparingDouble(Request::getGenerationTime));
        currentBatch.addAll(members);
        currentBatchSourceId = source.getAsInt();
        formedBatchCount++;
    }

    /**
     * Hands packet members to free devices until the packet has none left or no device is free.
     * Taking a member and registering its device happen together, inside this one call, so the
     * packet is never seen as finished while a member is being handed over.
     */
    private List<Request> dispatchBatch(double now) {
        List<Request> started = new ArrayList<>();
        while (!currentBatch.isEmpty()) {
            Device device = selectFreeDevice();
            if (device == null) {
                break;
            }
            Request next = currentBatch.pollFirst();
            batchDevices.add(device);
            device.startService(next, now);
            started.add(next);
        }
        return started;
    }

    /** Д2П2 — the first free device starting at the pointer, going round; null if all are busy. */
    private Device selectFreeDevice() {
        for (int step = 0; step < devices.length; step++) {
            int index = (devicePointer + step) % devices.length;
            if (devices[index].isFree()) {
                advancePointer(index);
                return devices[index];
            }
        }
        return null;
    }

    /** Moves the pointer to the device after the one just chosen. */
    private void advancePointer(int chosenIndex) {
        devicePointer = (chosenIndex + 1) % devices.length;
    }

    /** Number of the device the next search starts from (for step mode). */
    public int getPointerDeviceId() {
        return devices[devicePointer].getId();
    }

    /** Source of the active packet, or empty if no packet is active (for step mode). */
    public OptionalInt getActiveBatchSourceId() {
        return isBatchActive() ? OptionalInt.of(currentBatchSourceId) : OptionalInt.empty();
    }
    /** How many packets have been formed since the start (lets the simulator report a new one). */
    public int getFormedBatchCount() {
        return formedBatchCount;
    }

    /** Packet members not yet handed to a device, in service order (read-only copy, for step mode). */
    public List<Request> getPendingBatchMembers() {
        return List.copyOf(currentBatch);
    }
}