# СМО (Queuing System) — Variant 24. Primary Design Artifacts (Stage 2)

Domain: **Commodity Trading and Supply Chain Management Platform**
Variant 24: `ИБ · ИЗ2 · ПЗ1 · Д1ОЗ3 · Д1ОО2 · Д2П2 · Д2Б5 · ОР1 · ОД3`

Design of a discrete-event simulation model of a queuing system (СМО) for a commodity trading
and supply chain platform. The model consists of 5 sources (Buyer System, Supplier System,
Logistics System, Warehouse/Inventory System, Internal Trading System — the source number sets
the priority: source 1 is the highest, source 5 the lowest), a 20-slot buffer, 4 service devices
(Worker 1–4), a placement dispatcher **ДП** (disciplines Д1ОЗ3 + Д1ОО2) and a selection
dispatcher **ДВ** (disciplines Д2П2 + Д2Б5). Both dispatchers are algorithmic components of the
model, not human roles. Discipline codes are written in their standard notation.

## Repository Contents

| File | Description |
|---|---|
| `hegazy_V24_BusinessDomain.pdf` | Stage 1 — business domain description |
| `hegazy_V24_mapping.pdf` | Stage 1 — mapping of the domain onto the СМО model |
| `class-diagram.mmd` / `.png` | Class diagram |
| `main-sequence.mmd` / `.png` | Main sequence diagram — full request lifecycle |
| `sequence-arrival.mmd` / `.png` | Sequence diagram — request arrival |
| `sequence-device-free.mmd` / `.png` | Sequence diagram — device becomes free |
| `flowchart-swimlanes.mmd` / `.png` | Flowchart with swimlanes |

## 0. Stage 1 — Business Domain & Mapping

- [hegazy_V24_BusinessDomain.pdf](hegazy_V24_BusinessDomain.pdf)
- [hegazy_V24_mapping.pdf](hegazy_V24_mapping.pdf)

## 1. Class Diagram

![class diagram](class-diagram.png)

Mermaid source: [`class-diagram.mmd`](class-diagram.mmd)

**Design notes:**
- `Source` is one class with 5 instances (not 5 subclasses) — they differ only in `name` and
  `priority`. `priority` is the source number (a lower number means a higher priority, as defined in the
  Stage 1 mapping) and is what Д1ОО2 and Д2Б5 compare.
- `Request` is the final, agreed definition of "Заявка": one complete trade/order operation
  from a single source (not its internal steps).
- Every attribute is **private** (`-`); only the operations a collaborator actually needs are
  public (`+`). Both dispatchers hold the `Buffer` through a **private** field, and ДВ also
  holds the `Device` array privately (it needs it for the Д2П2 round-robin scan) — so callers
  never pass internal structures as parameters.
- The dispatchers **return** their results instead of reaching back into the `Simulator`:
  `PlacementDispatcher.onRequestArrival(r)` returns the refused request (or `null`),
  `SelectionDispatcher.tryServeDirectly(r)` returns whether `r` was put straight into service,
  and `SelectionDispatcher.onDeviceFree(d)` returns the list of requests it started. The
  `Simulator` then records the corresponding statistics.
- `SelectionDispatcher` (ДВ) owns device selection (Д2П2) and batch selection (Д2Б5). Its
  public interface is just `tryServeDirectly(r)` (arrival event) and `onDeviceFree(d)`
  (service-completion event); everything else — `isBatchActive()`, `formNewBatch()`,
  `dispatchBatch()`, `selectFreeDevice()`, `advancePointer()` — is private.
- Batch state in ДВ: `currentBatch` holds the frozen batch's members **not yet assigned** to a
  device, `currentBatchSourceId` records which source it was frozen from, and `batchDevices`
  holds the devices **currently in service** on a batch member.
  `isBatchActive() = !currentBatch.isEmpty() || !batchDevices.isEmpty()`.
- `Statistics` is kept separate from `Simulator` so it can feed both ОР1 (summary table) and
  ОД3 (time diagrams / live state) without duplicating counting logic. `getTimeDiagram()` here
  only means "the data this class can supply for ОД3" — it is not a separate diagram
  artifact at this stage.

## 2. Main Sequence Diagram — Full Request Lifecycle

This is the primary sequence diagram: it follows one request from generation all the way
through to service, showing every alternative branch required by variant 24 (immediate
service, buffering, refusal, and the batch-aware device-free path) in a single flow. Every
query whose answer decides a branch (`isFree()`, `isFull()`, `highestPrioritySourcePresent()`,
`tryServeDirectly()`, `onRequestArrival()`) shows its return message before the branch.

![main sequence diagram](main-sequence.png)

Mermaid source: [`main-sequence.mmd`](main-sequence.mmd)

### Д2Б5 — Batch Discipline

1. **Batch formation.** A new batch forms only when no batch is active (`currentBatch` and
   `batchDevices` both empty). ДВ finds the highest-priority source that still has requests in
   the buffer and freezes **all** of that source's buffered requests into `currentBatch` at
   once. Requests from that source that arrive later do **not** join this batch.
2. **Dispatching.** After every device-free event — whether or not a batch was just formed —
   ДВ runs `dispatchBatch()`: while `currentBatch` still has unassigned members **and any**
   device is free, it picks the next free device by Д2П2 (round-robin from the pointer),
   advances the pointer past it, takes the next batch member, registers the device in
   `batchDevices`, and starts service. So when a new batch forms, **every** free device gets a
   member, not only the device whose completion triggered the event.
3. **Completion.** A device is removed from `batchDevices` only when its service finishes (the
   first step of `onDeviceFree()`). If `currentBatch` is empty but `batchDevices` is not, the
   batch is **not finished**: free devices stay idle and the next batch is not formed. Only
   when the last member's service completes does ДВ form the next batch.
4. **No bypassing.** A new arrival is served directly only if no batch is active **and** a free
   device exists. Otherwise it goes to ДП (buffer, Д1ОЗ3, or refusal, Д1ОО2) even if some device
   happens to be free.
5. **Atomicity.** "Take the next member out of `currentBatch`" and "add the device to
   `batchDevices`" are two statements of the same handler. The simulator is single-threaded
   and processes one event at a time to completion, so no other event can ever observe the
   state between them — `isBatchActive()` cannot wrongly read "no batch" mid-assignment.
6. **ПЗ1.** Every `startService()` call, direct or from a batch, draws its service duration from
   the exponential distribution.

### Invariants

- **Invariant I — no device is idle while `currentBatch` has unassigned members.**
  `currentBatch` gains members only inside `onDeviceFree()` (via `formNewBatch()`), which is
  immediately followed by `dispatchBatch()`; that loop stops only when `currentBatch` is empty
  or no device is free. A device becomes free only through service completion, which also
  calls `onDeviceFree()` → `dispatchBatch()`. Arrivals never free a device and never add to
  `currentBatch`. So the invariant holds after every event — and this is why the arrival path
  has nothing to dispatch when it routes a request to the buffer during an active batch.
- **Invariant II — no device is idle while there's no active batch and the buffer is
  non-empty.** The buffer only grows when all devices are busy or a batch is active. When the
  last member of a batch finishes, the same `onDeviceFree()` call immediately forms the next
  batch if the buffer has anything in it. So a direct assignment on arrival can never let a
  new request overtake requests already waiting in the buffer.

## 3. Supplementary Sequence Diagrams

The two diagrams below isolate the two trigger events of the main diagram (a request arriving,
and a device becoming free) as standalone views. They follow exactly the same rules as
section 2.

### 3.1 Request Arrival

![sequence: arrival](sequence-arrival.png)

Mermaid source: [`sequence-arrival.mmd`](sequence-arrival.mmd)

### 3.2 Device Becomes Free

![sequence: device free](sequence-device-free.png)

Mermaid source: [`sequence-device-free.mmd`](sequence-device-free.mmd)

## 4. Flowchart with Swimlanes

The full process as a flowchart, with one swimlane per responsible actor: Source, Simulator,
Selection Dispatcher, Placement Dispatcher, Buffer, Worker/Devices. It covers immediate
processing, buffering, refusal, and the full Д2Б5 lifecycle — including the `dispatchBatch()`
loop that hands batch members to every free device, and the rule that the next batch cannot
form while any device is still serving a member of the current one.

![flowchart with swimlanes](flowchart-swimlanes.png)

Mermaid source: [`flowchart-swimlanes.mmd`](flowchart-swimlanes.mmd)

## 5. How the Artifacts Fit Together

- The **main sequence diagram** (section 2) is the authoritative, complete picture of a
  request's lifecycle and should be read first.
- The **two supplementary sequence diagrams** (section 3) isolate its two trigger events for
  closer inspection.
- The **flowchart with swimlanes** (section 4) shows the same process from a control-flow /
  responsibility angle rather than a message-exchange angle.
- The **class diagram** (section 1) is the static structure all of the above assume: every
  message and every decision in the dynamic diagrams corresponds to an operation or a private
  field declared there.
