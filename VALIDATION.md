# Tick Data Validation System

This document describes the validation system for detecting missing tick data in the stock streaming platform.

## Overview

The validation system tracks tick sequences using tick IDs to detect gaps in the data flow from producer → Kafka → consumer. This ensures data completeness in your simulation model.

## How It Works

### Core Concept: Sequence Tracking

1. **Tick ID Sequencing**: Each tick has a unique `tickId` that should increment sequentially per symbol
2. **Gap Detection**: The system tracks which IDs have been received and identifies missing sequences
3. **Completeness Calculation**: Calculates percentage of expected data that was actually received

### Example Gap Detection

```
Expected: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10
Received: 1, 2, 3, 5, 6, 10
Gaps: [4-4], [7-9]
Missing: 4, 7, 8, 9 (4 ticks out of 10 = 60% complete)
```

## Architecture

### Components

1. **ValidationResult** (`validation/ValidationResult.java`)
   - Immutable result containing validation statistics
   - Includes gap details and completeness percentage

2. **TickValidator** (`validation/TickValidator.java`)
   - Thread-safe validator tracking tick sequences per symbol
   - Uses concurrent data structures for high-throughput scenarios

3. **TickStore Integration** (`dashboard/store/TickStore.java`)
   - Automatically records every tick for validation
   - Provides validation API methods

4. **ValidationController** (`dashboard/api/ValidationController.java`)
   - REST API for accessing validation results
   - Health monitoring endpoints

## API Endpoints

### REST API

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/validation` | GET | Get validation results for all symbols |
| `/api/validation/{symbol}` | GET | Get validation for specific symbol |
| `/api/validation/{symbol}/summary` | GET | Get concise summary for symbol |
| `/api/validation/symbols` | GET | Get list of tracked symbols |
| `/api/validation/{symbol}/reset` | POST | Reset validation for symbol |
| `/api/validation/reset-all` | POST | Reset all validation data |
| `/api/validation/health` | GET | Get overall validation health status |

### Response Examples

**Complete Validation Result:**
```json
{
  "symbol": "AAPL",
  "expectedCount": 100,
  "actualCount": 98,
  "missingCount": 2,
  "completenessPercentage": 98.0,
  "gaps": [
    {"startId": 15, "endId": 16, "gapSize": 2}
  ],
  "isComplete": false,
  "validationTime": "2026-05-09T10:30:00Z"
}
```

**Health Check:**
```json
{
  "isHealthy": true,
  "totalSymbols": 5,
  "completeSymbols": 4,
  "incompleteSymbols": 1,
  "averageCompleteness": 99.2,
  "timestamp": 1234567890000
}
```

## Usage Examples

### Basic Usage

```bash
# Check all symbols validation status
curl http://localhost:8080/api/validation

# Check specific symbol
curl http://localhost:8080/api/validation/AAPL

# Get summary view
curl http://localhost:8080/api/validation/AAPL/summary

# Health check
curl http://localhost:8080/api/validation/health
```

### Programmatic Usage

```java
// Get validation result for a symbol
ValidationResult result = tickStore.validateSymbol("AAPL");

if (result.isComplete()) {
    System.out.println("No data loss detected");
} else {
    System.out.printf("Data loss detected: %.2f%% complete%n", 
        result.completenessPercentage());
    
    result.gaps().forEach(gap -> 
        System.out.printf("Gap: %s%n", gap));
}

// Validate all symbols
Map<String, ValidationResult> allResults = tickStore.validateAll();
allResults.forEach((symbol, result) -> {
    if (!result.isComplete()) {
        System.err.printf("Symbol %s has data loss!%n", symbol);
    }
});
```

## Integration Points

### Automatic Tracking

The validation system is automatically integrated with the tick processing flow:

```
Kafka Consumer → Tick Store → Validator
     ↓              ↓           ↓
   Process       Store      Track ID
```

Every tick processed by the consumer is automatically validated.

### Metrics Collection

Validation data complements the metrics collection system:

- **Metrics**: Performance statistics (throughput, latency)
- **Validation**: Data completeness (gaps, missing ticks)

## Testing

### Unit Tests

```bash
# Run validator unit tests
mvn test -Dtest=TickValidatorTest
```

### Integration Tests

```bash
# Run validation integration tests
mvn test -Dtest=TickValidationIT
```

## Configuration

### Gap Reporting Limit

By default, the validator reports up to 100 gaps per symbol. You can configure this:

```java
// Limit to 10 gaps per symbol
TickValidator validator = new TickValidator(10);
```

## Performance Considerations

- **Memory Usage**: O(n) where n = number of unique tick IDs per symbol
- **Tracking Overhead**: Minimal (~100 bytes per tick ID)
- **Concurrent Safety**: Thread-safe using ConcurrentHashMap
- **Gap Detection**: Optimized to stop after reaching max gap limit

## Troubleshooting

### High Missing Count

1. **Check Kafka lag**: High consumer lag might cause out-of-order processing
2. **Verify producer sequencing**: Ensure producer sends ticks in correct order
3. **Check network issues**: Network problems may cause message loss

### Incomplete Validation

1. **Consumer restart**: New consumer might miss some messages during rebalance
2. **Multiple producers**: Ensure producers coordinate tick ID generation
3. **Topic retention**: Short retention periods might delete old messages

## Best Practices

1. **Start validation early**: Begin tracking from the first tick
2. **Monitor health endpoint**: Set up alerts for completeness drops
3. **Regular gap analysis**: Periodically analyze gap patterns
4. **Coordinate tick IDs**: Ensure producers use consistent ID generation
5. **Set retention policies**: Ensure Kafka retention covers your validation window

## Future Enhancements

- **Real-time alerts**: WebSocket notifications for new gaps
- **Gap root cause analysis**: Correlate gaps with system events
- **Predictive analysis**: Predict potential data loss based on patterns
- **Integration with monitoring**: Export to Prometheus/Grafana