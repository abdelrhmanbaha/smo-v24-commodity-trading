# СМО (Queuing System) — Variant 24. Primary Artifacts (Class Diagram + Sequence Diagrams)

Domain: **Commodity Trading and Supply Chain Management Platform**
Variant 24: `ИБ · ИЗ2 · ПЗ1 · Д1ОЗ3 · Д1ОО2 · Д2П2 · Д2Б5 · ОР1 · ОД3`

These diagrams implement the business domain and mapping already approved by the instructor:
5 sources (Buyer System, Supplier System, Logistics System, Warehouse/Inventory System,
Internal Trading System — priority = source number), a 20-slot buffer, 4 service devices
(Worker 1–4), a placement dispatcher **ДП** (disciplines Д1ОЗ3 + Д1ОО2), and a selection
dispatcher **ДВ** (disciplines Д2П2 + Д2Б5). Both dispatchers are algorithmic components of
the simulation model, not human roles — the codes (ИБ, ИЗ2, ПЗ1, Д1ОЗ3, Д1ОО2, Д2П2, Д2Б5,
ОР1, ОД3, ДП, ДВ) are kept exactly as specified by the course, since they're the official
variant-24 vocabulary and aren't meant to be translated.

## 1. Class Diagram

![class diagram](class-diagram.png)

```mermaid
classDiagram
    direction LR

    class Source {
        +int id
        +string name
        +int priority
        +float nextArrivalTime
        +generateNextRequest() Request
    }

    class Request {
        +int id
        +int sourceId
        +float timeGenerated
        +float serviceStartTime
        +float serviceEndTime
        +RequestStatus status
    }

    class Buffer {
        +int capacity
        +Request[] slots
        +isFull() bool
        +isEmpty() bool
        +placeRequest(Request r) bool
        +findLowestPriorityRequest() Request
        +remove(Request r)
    }

    class PlacementDispatcher {
        <<ДП>>
        +onRequestArrival(Request r, Buffer b)
        -applyRefusalDiscipline(Buffer b) Request
    }

    class SelectionDispatcher {
        <<ДВ>>
        -int devicePointer
        +selectDevice(Device[] freeDevices) Device
        +selectNextRequest(Buffer b) Request
    }

    class Device {
        +int id
        +DeviceStatus status
        +Request currentRequest
        +startService(Request r)
        +completeService() Request
    }

    class Simulator {
        +float currentTime
        +Source[] sources
        +Buffer buffer
        +PlacementDispatcher dispatcherIn
        +SelectionDispatcher dispatcherOut
        +Device[] devices
        +Statistics stats
        +run()
        +step()
        +onRequestArrival(Request r)
        +onServiceComplete(Device d)
    }

    class Statistics {
        +recordArrival(Request r)
        +recordServiceStart(Request r)
        +recordServiceEnd(Request r)
        +recordRefusal(Request r)
        +getSummaryTable() SummaryTable
        +getTimeDiagram() TimeDiagram
    }

    class RequestStatus {
        <<enumeration>>
        WAITING
        IN_SERVICE
        SERVED
        REFUSED
    }

    class DeviceStatus {
        <<enumeration>>
        FREE
        BUSY
    }

    Simulator "1" --> "5" Source : owns
    Simulator "1" --> "1" Buffer : owns
    Simulator "1" --> "1" PlacementDispatcher : owns
    Simulator "1" --> "1" SelectionDispatcher : owns
    Simulator "1" --> "4" Device : owns
    Simulator "1" --> "1" Statistics : owns
    Source ..> Request : creates
    Buffer "1" o-- "0..20" Request : holds
    PlacementDispatcher ..> Buffer : places into / refuses from
    SelectionDispatcher ..> Buffer : pulls from
    SelectionDispatcher ..> Device : assigns to
    Device ..> Request : processes
    Request --> RequestStatus
    Device --> DeviceStatus
```

**Design notes:**
- `Source` is one class with 5 instances (not 5 subclasses) — they differ only in `name` and
  `priority`. `priority` doubles as the source number used by Д1ОО2 and Д2Б5.
- `Request` is the final, agreed definition of "Заявка": one complete trade/order operation
  from a single source (not its internal steps).
- `PlacementDispatcher` (ДП) only acts when every device is busy — it owns buffer placement
  (Д1ОЗ3) and, on overflow, refusal (Д1ОО2).
- `SelectionDispatcher` (ДВ) owns device selection (Д2П2) and buffer selection (Д2Б5) — both
  methods are used both for immediate service on arrival (choosing among several free devices)
  and when a device frees up later.
- `Statistics` is kept separate from `Simulator` so it can feed both ОР1 (summary table) and
  ОД3 (time diagrams / live state) without duplicating counting logic.

## 2. Sequence Diagram — Request Arrival

![sequence: arrival](sequence-arrival.png)

```mermaid
sequenceDiagram
    participant S as Source i
    participant Sim as Simulator
    participant ДВ as SelectionDispatcher (ДВ)
    participant ДП as PlacementDispatcher (ДП)
    participant D as Devices[1..4]
    participant B as Buffer (cap. 20)

    S->>Sim: generate Request r (time Tвх)
    Sim->>D: any device free?

    alt at least one device is free
        Sim->>ДВ: selectDevice(freeDevices)
        Note right of ДВ: Д2П2 — round-robin:<br/>scan from pointer, pick<br/>first free device
        ДВ-->>Sim: device k
        Sim->>D: device k.startService(r)
    else all 4 devices busy
        Sim->>ДП: onRequestArrival(r)
        ДП->>B: isFull()?
        alt buffer has a free slot
            Note right of ДП: Д1ОЗ3 — first free slot<br/>from the start, no shifting
            ДП->>B: placeRequest(r)
        else buffer full (all 20 slots)
            Note right of ДП: Д1ОО2 — refuse the request<br/>whose source has lowest priority
            ДП->>B: findLowestPriorityRequest()
            B-->>ДП: r_old (lowest-priority)
            ДП->>B: remove(r_old)
            ДП->>Sim: recordRefusal(r_old)
            ДП->>B: placeRequest(r)
        end
    end
```

Covers all three possible outcomes of a request arriving: immediate service, buffering,
refusal. Key architectural point: device selection (Д2П2) is **ДВ**'s job, not ДП's, even at
the moment of arrival — because it's a device-selection decision, not a buffering decision.
ДП only gets involved once no device is free.

## 3. Sequence Diagram — Device Becomes Free

![sequence: device free](sequence-device-free.png)

```mermaid
sequenceDiagram
    participant D as Device k
    participant Sim as Simulator
    participant ДВ as SelectionDispatcher (ДВ)
    participant B as Buffer (cap. 20)

    D->>Sim: serviceComplete(r)
    Sim->>Sim: recordServiceEnd(r)
    Sim->>B: isEmpty()?

    alt buffer is not empty
        Sim->>ДВ: onDeviceFree(device k)
        Note right of ДВ: Д2Б5 — find the highest-<br/>priority source with requests<br/>still in the buffer ("packet")
        ДВ->>B: selectNextRequest()
        B-->>ДВ: r_next (from top-priority packet)
        ДВ->>B: remove(r_next)
        ДВ->>D: device k.startService(r_next)
        Sim->>Sim: recordServiceStart(r_next)
    else buffer is empty
        D->>D: become idle, wait for next arrival
    end
```
