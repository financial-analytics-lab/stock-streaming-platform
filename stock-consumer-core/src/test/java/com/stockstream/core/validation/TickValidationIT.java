package com.stockstream.core.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stockstream.core.config.ConsumerConfig;
import com.stockstream.core.consumer.TickConsumer;
import com.stockstream.core.model.Tick;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class TickValidationIT {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.1");

    private KafkaProducer<String, byte[]> createProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        return new KafkaProducer<>(props);
    }

    private ConsumerConfig consumerConfig() {
        return new ConsumerConfig(
                KAFKA.getBootstrapServers(),
                Duration.ofMillis(500), false, "earliest", 100, null);
    }

    private byte[] tickJson(long id, String symbol, double price) throws Exception {
        Tick tick = new Tick(id, "Name", symbol, BigDecimal.valueOf(price), 1000L,
                Instant.parse("2026-01-15T10:30:00Z"), Instant.parse("2026-01-15T10:30:01Z"), 0);
        return MAPPER.writeValueAsBytes(tick);
    }

    @Test
    void testCompleteValidationFlow() throws Exception {
        String topic = "test-validation-" + System.nanoTime();
        String symbol = "AAPL";
        TickValidator validator = new TickValidator();

        // Send ticks with a gap
        try (var producer = createProducer()) {
            // Send ticks 1-3
            for (long i = 1; i <= 3; i++) {
                producer.send(new ProducerRecord<>(topic, symbol, tickJson(i, symbol, 150.0 + i)));
            }
            // Skip tick 4 (gap)
            // Send ticks 5-6
            for (long i = 5; i <= 6; i++) {
                producer.send(new ProducerRecord<>(topic, symbol, tickJson(i, symbol, 150.0 + i)));
            }
            producer.flush();
        }

        List<Tick> received = new CopyOnWriteArrayList<>();
        var consumer = new TickConsumer(consumerConfig(), topic, "group-validation", tick -> {
            received.add(tick);
            validator.recordTick(tick);
        });

        Thread thread = new Thread(consumer::start);
        thread.start();

        await().atMost(30, TimeUnit.SECONDS).until(() -> received.size() >= 5);
        consumer.shutdown();
        thread.join(5000);

        // Validate results
        ValidationResult result = validator.validateSymbol(symbol);

        assertEquals(5, received.size());
        assertEquals(symbol, result.symbol());
        assertEquals(6, result.expectedCount()); // 1-6
        assertEquals(5, result.actualCount());
        assertEquals(1, result.missingCount());
        assertEquals(1, result.gaps().size());
        assertFalse(result.isComplete());
        assertEquals(5.0 / 6.0 * 100.0, result.completenessPercentage(), 0.01);

        ValidationResult.Gap gap = result.gaps().get(0);
        assertEquals(4, gap.startId());
        assertEquals(4, gap.endId());
        assertEquals(1, gap.gapSize());
    }

    @Test
    void testConcurrentValidationTracking() throws Exception {
        String topic = "test-concurrent-" + System.nanoTime();
        String[] symbols = {"AAPL", "GOOG", "MSFT"};
        TickValidator validator = new TickValidator();

        // Send interleaved ticks for multiple symbols
        try (var producer = createProducer()) {
            for (int round = 0; round < 10; round++) {
                for (int symIdx = 0; symIdx < symbols.length; symIdx++) {
                    long tickId = (round * symbols.length) + symIdx + 1;
                    producer.send(new ProducerRecord<>(topic, symbols[symIdx],
                            tickJson(tickId, symbols[symIdx], 100.0 + tickId)));
                }
            }
            producer.flush();
        }

        List<Tick> received = new CopyOnWriteArrayList<>();
        var consumer = new TickConsumer(consumerConfig(), topic, "group-concurrent", tick -> {
            received.add(tick);
            validator.recordTick(tick);
        });

        Thread thread = new Thread(consumer::start);
        thread.start();

        await().atMost(30, TimeUnit.SECONDS).until(() -> received.size() >= 30);
        consumer.shutdown();
        thread.join(5000);

        // Validate all symbols
        Map<String, ValidationResult> results = validator.validateAll();

        assertEquals(3, results.size());
        for (String symbol : symbols) {
            ValidationResult result = results.get(symbol);
            assertNotNull(result);
            assertEquals(10, result.actualCount());
            assertTrue(result.isComplete());
            assertEquals(100.0, result.completenessPercentage(), 0.01);
        }
    }

    @Test
    void testValidationWithPoisonPills() throws Exception {
        String topic = "test-poison-validation-" + System.nanoTime();
        String symbol = "TEST";
        TickValidator validator = new TickValidator();

        // Send mix of valid and invalid messages
        try (var producer = createProducer()) {
            producer.send(new ProducerRecord<>(topic, symbol, tickJson(1, symbol, 100.0)));
            producer.send(new ProducerRecord<>(topic, "INVALID", "not json".getBytes(StandardCharsets.UTF_8)));
            producer.send(new ProducerRecord<>(topic, symbol, tickJson(2, symbol, 101.0)));
            producer.send(new ProducerRecord<>(topic, symbol, tickJson(4, symbol, 103.0))); // Gap at 3
            producer.flush();
        }

        List<Tick> received = new CopyOnWriteArrayList<>();
        var consumer = new TickConsumer(consumerConfig(), topic, "group-poison", tick -> {
            received.add(tick);
            validator.recordTick(tick);
        });

        Thread thread = new Thread(consumer::start);
        thread.start();

        await().atMost(30, TimeUnit.SECONDS).until(() -> received.size() >= 3);
        consumer.shutdown();
        thread.join(5000);

        ValidationResult result = validator.validateSymbol(symbol);

        assertEquals(3, received.size());
        assertEquals(4, result.expectedCount()); // 1-4
        assertEquals(3, result.actualCount());
        assertEquals(1, result.missingCount()); // Missing tick 3
        assertFalse(result.isComplete());
    }
}