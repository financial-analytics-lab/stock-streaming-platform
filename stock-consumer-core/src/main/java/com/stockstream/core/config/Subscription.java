package com.stockstream.core.config;

/**
 * A topic + consumer-group pair. Each Kafka consumer in the system owns one of these.
 * Two consumers must never share the same {@code groupId} unless they are
 * intentionally cooperating on the same topic.
 */
public record Subscription(String topic, String groupId) {
    public Subscription {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic is required");
        }
        if (groupId == null || groupId.isBlank()) {
            throw new IllegalArgumentException("groupId is required");
        }
    }
}
