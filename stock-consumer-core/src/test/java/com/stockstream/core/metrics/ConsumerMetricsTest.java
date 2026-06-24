package com.stockstream.core.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConsumerMetricsTest {

    @Test
    void testMetricsCollection() throws InterruptedException {
        // Given
        ConsumerMetrics metrics = new ConsumerMetrics("test-consumer");

        // When
        metrics.recordMessage(10, 100);
        metrics.recordMessage(20, 200);
        metrics.recordMessage(15, 150);

        // Then
        MetricsSnapshot snapshot = metrics.getSnapshot();

        assertEquals("test-consumer", snapshot.consumerName());
        assertEquals(3, snapshot.totalMessages());
        assertEquals(15.0, snapshot.avgProcessingTimeMs(), 0.01);
        assertEquals(150.0, snapshot.avgEndToEndLatencyMs(), 0.01);
        assertEquals(20, snapshot.maxProcessingTimeMs());
        assertEquals(200, snapshot.maxEndToEndLatencyMs());
        assertTrue(snapshot.throughputMessagesPerSecond() > 0);
        assertTrue(snapshot.elapsedMs() >= 0);
    }

    @Test
    void testReset() {
        // Given
        ConsumerMetrics metrics = new ConsumerMetrics("test-consumer");
        metrics.recordMessage(10, 100);

        // When
        metrics.reset();
        MetricsSnapshot snapshot = metrics.getSnapshot();

        // Then
        assertEquals(0, snapshot.totalMessages());
        assertEquals(0.0, snapshot.avgProcessingTimeMs(), 0.01);
        assertEquals(0.0, snapshot.avgEndToEndLatencyMs(), 0.01);
    }

    @Test
    void testRegistry() {
        // Given
        MetricsRegistry registry = MetricsRegistry.getInstance();

        // When
        ConsumerMetrics metrics1 = registry.getOrCreateMetrics("consumer1");
        ConsumerMetrics metrics2 = registry.getOrCreateMetrics("consumer1");
        ConsumerMetrics metrics3 = registry.getOrCreateMetrics("consumer2");

        // Then
        assertSame(metrics1, metrics2);
        assertNotSame(metrics1, metrics3);
        assertEquals(2, registry.getAllMetrics().size());
    }
}