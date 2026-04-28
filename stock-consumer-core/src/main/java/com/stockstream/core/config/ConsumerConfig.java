package com.stockstream.core.config;

import java.time.Duration;
import java.util.Properties;

/**
 * Broker- and poll-level Kafka consumer settings shared across all topic subscriptions
 * in a single JVM. Topic and group.id are not part of this record because each
 * consumer subscribes to its own topic and lives in its own consumer group;
 * see {@link Subscription} and {@code AbstractKafkaConsumer}'s constructor.
 */
public record ConsumerConfig(
        String bootstrapServers,
        Duration pollTimeout,
        boolean enableAutoCommit,
        String autoOffsetReset,
        int maxPollRecords,
        Properties additionalProperties
) {
    public static final Duration DEFAULT_POLL_TIMEOUT = Duration.ofMillis(1000);
    public static final int DEFAULT_MAX_POLL_RECORDS = 500;

    public ConsumerConfig {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            throw new IllegalArgumentException("bootstrapServers is required");
        }
        if (pollTimeout == null) pollTimeout = DEFAULT_POLL_TIMEOUT;
        if (autoOffsetReset == null) autoOffsetReset = "earliest";
        if (additionalProperties == null) additionalProperties = new Properties();
    }

    /**
     * Build a base {@link Properties} for {@code KafkaConsumer}. Callers must apply
     * the per-consumer {@code group.id} themselves (each consumer has its own group).
     */
    public Properties toKafkaProperties() {
        Properties props = new Properties();
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, String.valueOf(enableAutoCommit));
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_RECORDS_CONFIG, String.valueOf(maxPollRecords));
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        props.putAll(additionalProperties);
        return props;
    }
}
