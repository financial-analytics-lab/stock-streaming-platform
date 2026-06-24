package com.stockstream.core.metrics;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe metrics collector for Kafka consumer performance monitoring.
 * Tracks response time, end-to-end latency, and throughput.
 */
public class ConsumerMetrics {
    private final String consumerName;
    private final AtomicLong totalMessages = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final AtomicLong totalLatency = new AtomicLong(0);
    private final AtomicLong maxProcessingTime = new AtomicLong(0);
    private final AtomicLong maxLatency = new AtomicLong(0);
    private final AtomicLong startTime = new AtomicLong(System.currentTimeMillis());

    public ConsumerMetrics(String consumerName) {
        this.consumerName = consumerName;
    }

    public void recordMessage(long processingTimeMs, long endToEndLatencyMs) {
        totalMessages.incrementAndGet();
        totalProcessingTime.addAndGet(processingTimeMs);
        totalLatency.addAndGet(endToEndLatencyMs);

        // Update max values
        updateMax(maxProcessingTime, processingTimeMs);
        updateMax(maxLatency, endToEndLatencyMs);
    }

    private void updateMax(AtomicLong maxValue, long newValue) {
        long currentMax;
        do {
            currentMax = maxValue.get();
            if (newValue <= currentMax) break;
        } while (!maxValue.compareAndSet(currentMax, newValue));
    }

    public MetricsSnapshot getSnapshot() {
        long messages = totalMessages.get();
        long totalTime = totalProcessingTime.get();
        long totalLatencyTime = totalLatency.get();

        long elapsedMs = System.currentTimeMillis() - startTime.get();
        double throughput = elapsedMs > 0 ? (messages * 1000.0) / elapsedMs : 0;

        return new MetricsSnapshot(
            consumerName,
            messages,
            messages > 0 ? (double) totalTime / messages : 0,
            messages > 0 ? (double) totalLatencyTime / messages : 0,
            maxProcessingTime.get(),
            maxLatency.get(),
            throughput,
            elapsedMs
        );
    }

    public void reset() {
        totalMessages.set(0);
        totalProcessingTime.set(0);
        totalLatency.set(0);
        maxProcessingTime.set(0);
        maxLatency.set(0);
        startTime.set(System.currentTimeMillis());
    }

    public String getConsumerName() {
        return consumerName;
    }
}