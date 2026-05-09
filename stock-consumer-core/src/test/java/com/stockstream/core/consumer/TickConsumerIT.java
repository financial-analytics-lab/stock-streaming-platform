package com.stockstream.core.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stockstream.core.config.ConsumerConfig;
import com.stockstream.core.exception.DeserializationException;
import com.stockstream.core.model.Tick;

import org.apache.kafka.clients.consumer.ConsumerRecord;
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
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class TickConsumerIT {

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
    void consumesTicksFromKafka() throws Exception {
        String topic = "test-consume-" + System.nanoTime();
        List<Tick> received = new CopyOnWriteArrayList<>();

        try (var producer = createProducer()) {
            for (int i = 1; i <= 5; i++) {
                producer.send(new ProducerRecord<>(topic, "AAPL", tickJson(i, "AAPL", 150.0 + i)));
            }
            producer.flush();
        }

        var consumer = new TickConsumer(consumerConfig(), topic, "group-consume", received::add);

        Thread thread = new Thread(consumer::start);
        thread.start();

        await().atMost(30, TimeUnit.SECONDS).until(() -> received.size() >= 5);
        consumer.shutdown();
        thread.join(5000);

        assertEquals(5, received.size());
        assertEquals("AAPL", received.get(0).symbol());
    }

    @Test
    void callsOnDeserializationErrorForPoisonPill() throws Exception {
        String topic = "test-poison-" + System.nanoTime();
        List<ConsumerRecord<String, byte[]>> badRecords = new CopyOnWriteArrayList<>();
        List<Tick> goodTicks = new CopyOnWriteArrayList<>();

        try (var producer = createProducer()) {
            producer.send(new ProducerRecord<>(topic, "AAPL", tickJson(1, "AAPL", 150.0)));
            producer.send(new ProducerRecord<>(topic, "BAD", "not json".getBytes(StandardCharsets.UTF_8)));
            producer.send(new ProducerRecord<>(topic, "GOOG", tickJson(2, "GOOG", 2800.0)));
            producer.flush();
        }

        var consumer = new AbstractKafkaConsumer<Tick>(
                consumerConfig(), topic, "group-poison", new MessageDeserializer<>(Tick.class)) {
            @Override
            protected void process(Tick tick) {
                goodTicks.add(tick);
            }

            @Override
            protected void onDeserializationError(ConsumerRecord<String, byte[]> record, DeserializationException e) {
                badRecords.add(record);
            }
        };

        Thread thread = new Thread(consumer::start);
        thread.start();

        await().atMost(30, TimeUnit.SECONDS).until(() -> goodTicks.size() >= 2 && badRecords.size() >= 1);
        consumer.shutdown();
        thread.join(5000);

        assertEquals(2, goodTicks.size());
        assertEquals(1, badRecords.size());
        assertEquals("BAD", badRecords.get(0).key());
    }

    @Test
    void callsOnProcessingErrorAndContinues() throws Exception {
        String topic = "test-proc-error-" + System.nanoTime();
        List<Tick> processed = new CopyOnWriteArrayList<>();
        List<Tick> errors = new CopyOnWriteArrayList<>();

        try (var producer = createProducer()) {
            producer.send(new ProducerRecord<>(topic, "FAIL", tickJson(1, "FAIL", 100.0)));
            producer.send(new ProducerRecord<>(topic, "AAPL", tickJson(2, "AAPL", 150.0)));
            producer.flush();
        }

        var consumer = new AbstractKafkaConsumer<Tick>(
                consumerConfig(), topic, "group-proc-err", new MessageDeserializer<>(Tick.class)) {
            @Override
            protected void process(Tick tick) {
                if ("FAIL".equals(tick.symbol())) {
                    throw new RuntimeException("Simulated processing failure");
                }
                processed.add(tick);
            }

            @Override
            protected void onProcessingError(Tick tick, Exception e) {
                errors.add(tick);
            }
        };

        Thread thread = new Thread(consumer::start);
        thread.start();

        await().atMost(30, TimeUnit.SECONDS).until(() -> processed.size() >= 1 && errors.size() >= 1);
        consumer.shutdown();
        thread.join(5000);

        assertEquals(1, processed.size());
        assertEquals("AAPL", processed.get(0).symbol());
        assertEquals(1, errors.size());
        assertEquals("FAIL", errors.get(0).symbol());
    }

    @Test
    void onStartAndOnStopAreCalled() throws Exception {
        String topic = "test-lifecycle-" + System.nanoTime();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch stopLatch = new CountDownLatch(1);

        var consumer = new AbstractKafkaConsumer<Tick>(
                consumerConfig(), topic, "group-lifecycle", new MessageDeserializer<>(Tick.class)) {
            @Override
            protected void process(Tick tick) {}

            @Override
            protected void onStart() {
                startLatch.countDown();
            }

            @Override
            protected void onStop() {
                stopLatch.countDown();
            }
        };

        Thread thread = new Thread(consumer::start);
        thread.start();

        assertTrue(startLatch.await(15, TimeUnit.SECONDS), "onStart should have been called");
        consumer.shutdown();
        assertTrue(stopLatch.await(15, TimeUnit.SECONDS), "onStop should have been called");
        thread.join(5000);
    }

    @Test
    void shutdownStopsConsumerGracefully() throws Exception {
        String topic = "test-shutdown-" + System.nanoTime();

        var consumer = new TickConsumer(consumerConfig(), topic, "group-shutdown", t -> {});

        Thread thread = new Thread(consumer::start);
        thread.start();

        await().atMost(10, TimeUnit.SECONDS).until(consumer::isRunning);
        assertTrue(consumer.isRunning());

        consumer.shutdown();
        thread.join(10000);

        assertFalse(consumer.isRunning());
        assertFalse(thread.isAlive());
    }

    @Test
    void doubleStartThrows() throws Exception {
        String topic = "test-double-start-" + System.nanoTime();

        var consumer = new TickConsumer(consumerConfig(), topic, "group-double", t -> {});

        Thread thread = new Thread(consumer::start);
        thread.start();

        await().atMost(10, TimeUnit.SECONDS).until(consumer::isRunning);

        assertThrows(IllegalStateException.class, consumer::start);

        consumer.shutdown();
        thread.join(5000);
    }
}
