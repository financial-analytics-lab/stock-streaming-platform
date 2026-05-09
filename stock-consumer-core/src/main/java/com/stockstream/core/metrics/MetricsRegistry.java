package com.stockstream.core.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing multiple consumer metrics instances.
 */
public class MetricsRegistry {
    private static final MetricsRegistry INSTANCE = new MetricsRegistry();
    private final Map<String, ConsumerMetrics> metricsMap = new ConcurrentHashMap<>();

    private MetricsRegistry() {
        // Private constructor for singleton pattern
    }

    public static MetricsRegistry getInstance() {
        return INSTANCE;
    }

    public ConsumerMetrics getOrCreateMetrics(String consumerName) {
        return metricsMap.computeIfAbsent(consumerName, ConsumerMetrics::new);
    }

    public ConsumerMetrics getMetrics(String consumerName) {
        return metricsMap.get(consumerName);
    }

    public Map<String, MetricsSnapshot> getAllMetrics() {
        Map<String, MetricsSnapshot> snapshots = new ConcurrentHashMap<>();
        metricsMap.forEach((name, metrics) -> {
            snapshots.put(name, metrics.getSnapshot());
        });
        return snapshots;
    }

    public void resetMetrics(String consumerName) {
        ConsumerMetrics metrics = metricsMap.get(consumerName);
        if (metrics != null) {
            metrics.reset();
        }
    }

    public void resetAllMetrics() {
        metricsMap.values().forEach(ConsumerMetrics::reset);
    }
}