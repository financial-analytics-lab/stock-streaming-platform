package com.stockstream.core.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class ConsumerConfigTest {

    @Test
    void validConfigCreation() {
        ConsumerConfig config = new ConsumerConfig(
                "localhost:9092",
                Duration.ofMillis(500), false, "earliest", 100, null);

        assertEquals("localhost:9092", config.bootstrapServers());
        assertEquals(Duration.ofMillis(500), config.pollTimeout());
    }

    @Test
    void appliesDefaults() {
        ConsumerConfig config = new ConsumerConfig(
                "localhost:9092",
                null, false, null, 0, null);

        assertEquals(ConsumerConfig.DEFAULT_POLL_TIMEOUT, config.pollTimeout());
        assertEquals("earliest", config.autoOffsetReset());
        assertNotNull(config.additionalProperties());
    }

    @Test
    void rejectsNullBootstrapServers() {
        assertThrows(IllegalArgumentException.class, () ->
                new ConsumerConfig(null,
                        null, false, null, 0, null));
    }

    @Test
    void rejectsBlankBootstrapServers() {
        assertThrows(IllegalArgumentException.class, () ->
                new ConsumerConfig("  ",
                        null, false, null, 0, null));
    }

    @Test
    void toKafkaPropertiesContainsBrokerSettings() {
        ConsumerConfig config = new ConsumerConfig(
                "localhost:9092",
                null, false, "latest", 200, null);

        Properties props = config.toKafkaProperties();

        assertEquals("localhost:9092", props.get("bootstrap.servers"));
        assertEquals("latest", props.get("auto.offset.reset"));
        assertEquals("false", props.get("enable.auto.commit"));
        assertEquals("200", props.get("max.poll.records"));
        assertEquals("org.apache.kafka.common.serialization.ByteArrayDeserializer",
                props.get("value.deserializer"));
        // group.id is intentionally NOT set here — applied per-consumer
        assertNull(props.get("group.id"));
    }

    @Test
    void additionalPropertiesAreIncluded() {
        Properties extra = new Properties();
        extra.put("custom.setting", "custom-value");

        ConsumerConfig config = new ConsumerConfig(
                "localhost:9092",
                null, false, null, 0, extra);

        Properties props = config.toKafkaProperties();
        assertEquals("custom-value", props.get("custom.setting"));
    }
}
