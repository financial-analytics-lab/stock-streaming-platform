# Kafka Consumer Metrics Collection

This document describes the performance metrics collection system for the Kafka consumers in the stock streaming platform.

## Overview

The metrics collection system tracks three key performance indicators for each Kafka consumer:

1. **Response Time**: Time taken to process each message (from deserialization to handler completion)
2. **End-to-End Latency**: Time from when the message was created (Kafka record timestamp) to when it's processed
3. **Throughput**: Messages processed per second

## Architecture

### Core Components

1. **ConsumerMetrics** (`stock-consumer-core/src/main/java/com/stockstream/core/metrics/ConsumerMetrics.java`)
   - Thread-safe metrics collector for individual consumers
   - Tracks processing time, latency, and throughput statistics
   - Provides snapshot of current metrics

2. **MetricsSnapshot** (`stock-consumer-core/src/main/java/com/stockstream/core/metrics/MetricsSnapshot.java`)
   - Immutable data structure containing metrics at a point in time
   - Includes averages, max values, and throughput calculations

3. **MetricsRegistry** (`stock-consumer-core/src/main/java/com/stockstream/core/metrics/MetricsRegistry.java`)
   - Singleton registry managing multiple consumer metrics instances
   - Thread-safe storage using ConcurrentHashMap

4. **MetricsController** (`stock-consumer-dashboard/src/main/java/com/stockstream/dashboard/api/MetricsController.java`)
   - REST API for accessing metrics from the dashboard
   - Provides endpoints for retrieving and resetting metrics

### Integration Points

The metrics collection is automatically integrated into `AbstractKafkaConsumer`:

- Each consumer instance creates a `ConsumerMetrics` instance with name `{topic}-{groupId}`
- Metrics are collected for every successfully processed message
- Metrics collection has minimal performance impact (nanosecond precision)

## API Endpoints

### REST API

- `GET /api/metrics` - Get all metrics for all consumers
- `GET /api/metrics/{consumerName}` - Get metrics for specific consumer
- `POST /api/metrics/{consumerName}/reset` - Reset metrics for specific consumer
- `POST /api/metrics/reset-all` - Reset all metrics

### Consumer Metrics Format

```json
{
  "consumerName": "ticks-stock-ticks",
  "totalMessages": 15234,
  "avgProcessingTimeMs": 2.34,
  "avgEndToEndLatencyMs": 45.67,
  "maxProcessingTimeMs": 15,
  "maxEndToEndLatencyMs": 123,
  "throughputMessagesPerSecond": 125.4,
  "elapsedMs": 121523
}
```

## Usage Examples

### Accessing Metrics Programmatically

```java
// Get metrics for a specific consumer
ConsumerMetrics tickMetrics = tickConsumer.getMetrics();
MetricsSnapshot snapshot = tickMetrics.getSnapshot();

System.out.println("Throughput: " + snapshot.throughputMessagesPerSecond() + " msg/s");
System.out.println("Avg Processing Time: " + snapshot.avgProcessingTimeMs() + " ms");
```

### Using the REST API

```bash
# Get all metrics
curl http://localhost:8080/api/metrics

# Get specific consumer metrics
curl http://localhost:8080/api/metrics/ticks-stock-ticks

# Reset metrics for a consumer
curl -X POST http://localhost:8080/api/metrics/ticks-stock-ticks/reset

# Reset all metrics
curl -X POST http://localhost:8080/api/metrics/reset-all
```

## Metric Details

### Response Time (Processing Time)
- **What it measures**: Time from starting message deserialization to completing the `process()` method
- **Unit**: Milliseconds
- **Why it matters**: Indicates how efficiently your consumer is processing individual messages
- **Target**: Should typically be < 10ms for simple operations

### End-to-End Latency
- **What it measures**: Time from when the message was produced (Kafka record timestamp) to when it's processed
- **Unit**: Milliseconds  
- **Why it matters**: Total pipeline latency including Kafka queue time and processing time
- **Target**: Depends on use case, but lower is generally better

### Throughput
- **What it measures**: Messages processed per second
- **Unit**: Messages/second
- **Why it matters**: Indicates consumer capacity and helps with capacity planning
- **Target**: Should meet or exceed your expected message rate

## Performance Considerations

- Metrics collection uses `System.currentTimeMillis()` which has ~1ms precision
- Minimal overhead: atomic operations for thread-safe updates
- No impact on message ordering or processing guarantees
- Memory usage: ~100 bytes per metrics instance

## Testing

The metrics system includes comprehensive tests:

```bash
# Run unit tests for metrics
mvn test -Dtest=ConsumerMetricsTest

# Run all tests
mvn test
```

## Future Enhancements

Potential improvements for the metrics system:

1. **Histogram support** - More detailed distribution analysis
2. **Integration with monitoring systems** - Prometheus, Grafana, etc.
3. **Alerting thresholds** - Automatic alerts on performance degradation
4. **Windowed metrics** - Rolling time windows for recent performance
5. **Message size tracking** - Monitor processing time vs message size