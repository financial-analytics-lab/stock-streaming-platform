# Stock Streaming Platform

A modular, real-time stock market platform that ingests tick data from Apache Kafka, aggregates it into OHLCV candles, and streams everything to a live web dashboard.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17, Maven multi-module |
| Streaming | Apache Kafka 4.2.0, Kafka Streams |
| Backend | Spring Boot 3.3.5 (REST API + WebSocket) |
| Frontend | React 18, TypeScript, Vite, Tailwind CSS, lightweight-charts |
| Serialization | Jackson 2.17.3 |

## Project Structure

```
stock-streaming-platform/
├── pom.xml                        # Parent POM (dependency management)
├── stock-consumer-core/           # Reusable Kafka consumer library
├── stock-consumer-dashboard/      # Spring Boot backend (REST API + WebSocket)
├── stock-candle-processor/        # Kafka Streams OHLCV aggregator (fat jar)
├── stock-dashboard-ui/            # React + Vite frontend
└── publisher-service/             # Data publisher (separate service)
```

## Modules

| Module | Description |
|---|---|
| `stock-consumer-core` | Core library: Kafka consumer base class, data model, config, deserialization |
| `stock-consumer-dashboard` | Spring Boot app — consumes tick/news/candle topics, exposes REST API on `:8080` and broadcasts over WebSocket |
| `stock-candle-processor` | Kafka Streams app — aggregates raw ticks into OHLCV candles for multiple intervals (1s, 5s, 1m, 5m, 15m) |
| `stock-dashboard-ui` | React frontend — live price ticker, candlestick charts, news feed; proxies to `:8080` |

## Kafka Topics

| Topic | Direction | Description |
|---|---|---|
| `stock-ticks` | input | Raw tick events (price, volume, timestamp) |
| `news-events` | input | News feed events |
| `candles-1s` | output | 1-second OHLCV candles |
| `candles-5s` | output | 5-second OHLCV candles |
| `candles-1m` | output | 1-minute OHLCV candles |
| `candles-5m` | output | 5-minute OHLCV candles |
| `candles-15m` | output | 15-minute OHLCV candles |

## Prerequisites

- Java 17+
- Maven 3.6+
- Node.js 18+ and npm
- A running Apache Kafka broker (default: `localhost:9092`)

## Getting Started

### 1. Build the Java modules

From the project root:

```bash
mvn clean package
```

This builds all three Maven modules and produces the fat jars under their respective `target/` directories.

### 2. Configure the Dashboard

Edit `stock-consumer-dashboard/src/main/resources/consumer.properties`:

```properties
bootstrap.servers=localhost:9092
poll.timeout.ms=1000
auto.offset.reset=earliest

tick.topic=stock-ticks
tick.group.id=dashboard-tick-consumer

news.topic=news-events
news.group.id=dashboard-news-consumer
```

Environment variables override the file (prefix: `STOCK_CONSUMER_*`).

### 3. Configure the Candle Processor

Copy and edit the example:

```bash
cp stock-candle-processor/src/main/resources/candle-processor.properties.example \
   stock-candle-processor/src/main/resources/candle-processor.properties
```

```properties
bootstrap.servers=localhost:9092
application.id=candle-processor
input.topic=stock-ticks
num.stream.threads=2
state.dir=/tmp/kafka-streams/candle-processor
auto.offset.reset=earliest
```

Environment variables override the file (prefix: `CANDLE_*`).

### 4. Install frontend dependencies

```bash
cd stock-dashboard-ui
npm install
```

---

## Running the Platform

The platform requires **three terminals** running simultaneously.

### Terminal 1 — Dashboard (Spring Boot)

```bash
mvn -pl stock-consumer-dashboard spring-boot:run
```

Starts the REST API and WebSocket server on **http://localhost:8080**.

Alternatively, run the packaged jar:

```bash
java -jar stock-consumer-dashboard/target/stock-consumer-dashboard-1.0.0-SNAPSHOT.jar
```

### Terminal 2 — Candle Processor (Kafka Streams)

```bash
java -jar stock-candle-processor/target/stock-candle-processor-1.0.0-SNAPSHOT.jar
```

Reads raw ticks from `stock-ticks` and produces OHLCV candles to the `candles-*` topics.

### Terminal 3 — Frontend (Vite dev server)

```bash
cd stock-dashboard-ui
npm run dev
```

Opens the live dashboard at **http://localhost:3000**.

The Vite dev server proxies `/api/*` and `/ws` to `localhost:8080`, so no CORS configuration is needed during development.

---

## Architecture

```
[Publisher] ──► stock-ticks ──► [Candle Processor] ──► candles-{1s,5s,1m,5m,15m} ──┐
                   │                                                                   │
                   └──► [Dashboard Spring Boot :8080] ◄────────────────────────────────┘
                              │  REST /api/*
                              │  WebSocket /ws
                              ▼
                      [Frontend :3000]
```

1. **Publisher** pushes raw `Tick` events to `stock-ticks` (and news to `news-events`).
2. **Candle Processor** (Kafka Streams) aggregates ticks into windowed OHLCV candles and writes them to interval-specific topics.
3. **Dashboard** (Spring Boot) consumes all topics, stores recent history in memory, and serves:
   - REST endpoints for historical data queries
   - WebSocket broadcasts for live updates
4. **Frontend** (React/Vite) renders candlestick charts and a live ticker, receiving updates via WebSocket.

## REST API Reference

All endpoints are served at `localhost:8080` (or via the Vite proxy at `localhost:3000`).

| Method | Path | Description |
|---|---|---|
| GET | `/api/ticks/latest` | Latest tick per symbol |
| GET | `/api/ticks/{symbol}/history` | Tick history for a symbol (`?limit=200`) |
| GET | `/api/metrics` | Consumer metrics (total received, avg lag, symbol count) |
| GET | `/api/candles/intervals` | List of supported candle intervals |
| GET | `/api/candles/{symbol}/{interval}/history` | Candle history + live candle (`?limit=200`) |

WebSocket endpoint: `ws://localhost:8080/ws` — broadcasts `TICK`, `CANDLE`, and `NEWS` events as JSON.

## Data Model

### Tick

| Field | Type | Description |
|---|---|---|
| `tickId` | `String` | Unique tick identifier |
| `symbol` | `String` | Stock symbol |
| `price` | `BigDecimal` | Trade price |
| `volume` | `long` | Trade volume |
| `timestamp` | `Instant` | Event time |
| `publishedAt` | `Instant` | Publication time |

### Candle

| Field | Type | Description |
|---|---|---|
| `symbol` | `String` | Stock symbol |
| `interval` | `String` | Window size (e.g. `1m`) |
| `windowStart` | `Instant` | Window open time |
| `windowEnd` | `Instant` | Window close time |
| `open` | `BigDecimal` | Opening price |
| `high` | `BigDecimal` | Highest price |
| `low` | `BigDecimal` | Lowest price |
| `close` | `BigDecimal` | Closing price |
| `volume` | `long` | Total volume |
| `tickCount` | `int` | Number of ticks in window |
| `status` | `CandleStatus` | `OPEN` (in-progress) or `CLOSED` (window complete) |

## License

This project is part of a university graduation project.
