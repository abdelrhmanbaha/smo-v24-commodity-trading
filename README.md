# СМО — Вариант 24. Первичные артефакты (диаграмма классов + диаграммы последовательности)

Домен: **Commodity Trading and Supply Chain Management Platform**
Вариант 24: `ИБ · ИЗ2 · ПЗ1 · Д1ОЗ3 · Д1ОО2 · Д2П2 · Д2Б5 · ОР1 · ОД3`

Эти диаграммы напрямую реализуют бизнес-домен и маппинг, согласованные с преподавателем:
5 источников (Buyer System, Supplier System, Logistics System, Warehouse/Inventory System,
Internal Trading System, приоритет = номер источника), буфер на 20 мест, 4 обработчика
(Worker 1–4), диспетчер постановки заявок **ДП** (Д1ОЗ3 + Д1ОО2) и диспетчер выбора заявок
**ДВ** (Д2П2 + Д2Б5) — оба являются алгоритмическими компонентами модели, не людьми.

## 1. Диаграмма классов

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

**Почему так:**
- `Source` — 5 экземпляров одного класса (не 5 подклассов), различаются только `name`/`priority`;
  `priority` одновременно и есть номер источника, используемый в Д1ОО2 и Д2Б5.
- `Request` = «Заявка» в финальном согласованном определении: одна целостная торговая/заказная
  операция от одного источника (не её внутренние шаги).
- `PlacementDispatcher` (ДП) отвечает только за то, что происходит, когда все приборы заняты —
  постановка в буфер (Д1ОЗ3) и, при переполнении, отказ (Д1ОО2).
- `SelectionDispatcher` (ДВ) отвечает за выбор прибора (Д2П2) и выбор заявки из буфера (Д2Б5) —
  оба метода используются и при немедленном обслуживании (выбор среди нескольких свободных
  приборов), и при освобождении прибора.
- `Statistics` отделена от `Simulator`, чтобы одинаково питать и ОР1 (сводная таблица), и ОД3
  (временные диаграммы/текущее состояние), не дублируя логику подсчёта.

## 2. Диаграмма последовательности — приход заявки

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

Покрывает все три исхода прихода заявки: немедленное обслуживание, постановка в буфер,
отказ. Важная деталь архитектуры: выбор прибора (Д2П2) — обязанность **ДВ**, а не ДП, даже
в момент прихода заявки — потому что это выбор прибора, а не постановка в буфер. ДП
подключается только когда свободных приборов нет.

## 3. Диаграмма последовательности — освобождение прибора

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

## Источники (source files)

- `class-diagram.mmd` / `.png`
- `sequence-arrival.mmd` / `.png`
- `sequence-device-free.mmd` / `.png`

GitHub рендерит блоки ```mermaid``` в этом файле автоматически — отдельно открывать `.png`
не обязательно, но они приложены на случай локального просмотра или вставки в отчёт.
