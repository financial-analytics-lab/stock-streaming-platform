# Stock Streaming Platform

A lightweight, modular Java platform for consuming and processing real-time stock ticker data from Apache Kafka.

## Tech Stack

- **Java 21** (records, sealed classes)
- **Apache Kafka 4.2.0** for message streaming
- **Jackson 2.21.2** for JSON serialization
- **Maven** multi-module build

## Project Structure

```
stock-streaming-platform/
├── pom.xml                        # Parent POM (dependency management)
├── stock-consumer-core/           # Reusable consumer library
│   └── src/main/java/com/stockstream/core/
│       ├── config/                # ConfigLoader, ConsumerConfig
│       ├── consumer/              # AbstractTickConsumer, TickDeserializer
│       ├── exception/             # Custom exceptions
│       ├── logging/               # LoggerFactory wrapper
│       └── model/                 # Tick record
└── stock-consumer-dashboard/      # Dashboard consumer application
    └── src/main/java/com/stockstream/dashboard/
        └── DashboardApp.java
```

## Modules

| Module | Description |
|---|---|
| `stock-consumer-core` | Core library: Kafka consumer base class, data model, config, and deserialization |
| `stock-consumer-dashboard` | Concrete consumer that reads ticks and outputs to the console |

## Prerequisites

- Java 21+
- Maven 3.6+
- A running Apache Kafka broker (default: `localhost:9092`)
- A Kafka topic named `stock-ticks` (or configure a different one)

## Getting Started

### 1. Build

```bash
mvn clean package
```

### 2. Configure

**Option A** -- properties file (place on classpath):

```properties
bootstrap.servers=localhost:9092
group.id=my-group
topic=stock-ticks
poll.timeout.ms=1000
auto.offset.reset=earliest
max.poll.records=500
```

See [`consumer.properties.example`](stock-consumer-core/src/main/resources/consumer.properties.example) for a full template.

**Option B** -- environment variables (take precedence over the properties file):

```bash
export STOCK_CONSUMER_BOOTSTRAP_SERVERS=localhost:9092
export STOCK_CONSUMER_GROUP_ID=my-group
export STOCK_CONSUMER_TOPIC=stock-ticks
export STOCK_CONSUMER_LOG_LEVEL=INFO
```

### 3. Run the Dashboard Consumer

```bash
java -cp stock-consumer-dashboard/target/stock-consumer-dashboard-1.0.0-SNAPSHOT.jar \
  com.stockstream.dashboard.DashboardApp
```

## Architecture

The platform follows a **Template Method** pattern:

1. `AbstractTickConsumer` manages the full Kafka consumer lifecycle -- polling, deserialization, offset commits, and graceful shutdown.
2. Concrete consumers (e.g. `DashboardApp`) extend the base class and override hook methods:
   - `process(Tick)` -- handle each tick
   - `onDeserializationError()` / `onProcessingError()` -- error callbacks
   - `onStart()` / `onStop()` -- setup and teardown

This design keeps consumer implementations minimal while the core library handles all Kafka plumbing.

## Data Model

The `Tick` record represents a single stock market event:

| Field | Type | Description |
|---|---|---|
| `tickId` | `String` | Unique tick identifier |
| `symbol` | `String` | Stock symbol |
| `price` | `BigDecimal` | Trade price |
| `volume` | `long` | Trade volume |
| `timestamp` | `Instant` | Event time |
| `publishedAt` | `Instant` | Publication time |

## License

This project is part of a university graduation project.
