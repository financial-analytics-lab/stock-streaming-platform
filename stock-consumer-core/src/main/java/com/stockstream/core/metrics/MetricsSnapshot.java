package com.stockstream.core.metrics;

/**
 * Immutable snapshot of consumer metrics at a point in time.
 */
public record MetricsSnapshot(
    String consumerName,
    long totalMessages,
    double avgProcessingTimeMs,
    double avgEndToEndLatencyMs,
    long maxProcessingTimeMs,
    long maxEndToEndLatencyMs,
    double throughputMessagesPerSecond,
    long elapsedMs
) {
    @Override
    public String toString() {
        return String.format(
            "MetricsSnapshot[%s]: messages=%d, avgProc=%.2fms, avgLat=%.2fms, maxProc=%dms, maxLat=%dms, throughput=%.2f/s",
            consumerName, totalMessages, avgProcessingTimeMs, avgEndToEndLatencyMs,
            maxProcessingTimeMs, maxEndToEndLatencyMs, throughputMessagesPerSecond
        );
    }
}