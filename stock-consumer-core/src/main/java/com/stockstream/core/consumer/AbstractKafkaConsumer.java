package com.stockstream.core.consumer;

import com.stockstream.core.config.ConsumerConfig;
import com.stockstream.core.exception.DeserializationException;
import com.stockstream.core.logging.LoggerFactory;
import com.stockstream.core.metrics.ConsumerMetrics;
import com.stockstream.core.metrics.MetricsRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;

import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generic single-topic Kafka consumer. Subclasses bind a message type {@code T},
 * pass a {@link MessageDeserializer} for that type, and implement {@link #process}.
 *
 * <p>One instance owns one {@link KafkaConsumer} and is not thread-safe — run each
 * instance on its own thread.
 */
public abstract class AbstractKafkaConsumer<T> implements AutoCloseable {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final ConsumerConfig config;
    private final String topic;
    private final String groupId;
    private final MessageDeserializer<T> deserializer;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ConsumerMetrics metrics;
    private KafkaConsumer<String, byte[]> consumer;

    protected AbstractKafkaConsumer(ConsumerConfig config,
                                    String topic,
                                    String groupId,
                                    MessageDeserializer<T> deserializer) {
        if (config == null) throw new IllegalArgumentException("config is required");
        if (topic == null || topic.isBlank()) throw new IllegalArgumentException("topic is required");
        if (groupId == null || groupId.isBlank()) throw new IllegalArgumentException("groupId is required");
        if (deserializer == null) throw new IllegalArgumentException("deserializer is required");
        this.config = config;
        this.topic = topic;
        this.groupId = groupId;
        this.deserializer = deserializer;

        // Initialize metrics for this consumer
        String consumerName = String.format("%s-%s", topic, groupId);
        this.metrics = MetricsRegistry.getInstance().getOrCreateMetrics(consumerName);
    }

    /** Process a single deserialized message. Called once per record. */
    protected abstract void process(T message);

    /**
     * Called when a record fails deserialization.
     * Default: log and skip. Override to send to dead-letter topic, etc.
     */
    protected void onDeserializationError(ConsumerRecord<String, byte[]> record, DeserializationException e) {
        log.log(Level.WARNING, "Skipping undeserializable record at offset " +
                record.offset() + " partition " + record.partition(), e);
    }

    /**
     * Called when {@link #process} throws.
     * Default: log and skip. Override for retry logic, DLQ routing, etc.
     */
    protected void onProcessingError(T message, Exception e) {
        log.log(Level.SEVERE, "Error processing message: " + message, e);
    }

    /**
     * Override to supply the timestamp from the actual message payload for latency metric tracking.
     * Defaults to the Kafka Record timestamp.
     */
    protected long getEventTimestamp(T message, ConsumerRecord<String, byte[]> record) {
        return record.timestamp();
    }

    /** Called once before the poll loop starts, after subscription. */
    protected void onStart() {}

    /** Called once after the poll loop exits, before consumer.close(). */
    protected void onStop() {}

    /** Exposes topic to subclasses that override subscription behavior. */
    protected final String topic() {
        return topic;
    }

    /**
     * Subscription hook.
     * Default behavior: group subscription to the topic.
     * Subclasses can override to use explicit partition assignment.
     */
    protected void subscribeToSource(KafkaConsumer<String, byte[]> consumer) {
        consumer.subscribe(Collections.singletonList(topic));
    }

    // ---- Lifecycle ----

    /** Start the poll loop. Blocks the calling thread. */
    public final void start() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Consumer is already running");
        }

        Properties props = config.toKafkaProperties();
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG, groupId);
        consumer = new KafkaConsumer<>(props);
        subscribeToSource(consumer);
        log.info("Subscribed to topic '" + topic + "' with group '" + groupId + "'");

        onStart();

        try {
            while (running.get()) {
                ConsumerRecords<String, byte[]> records = consumer.poll(config.pollTimeout());

                for (ConsumerRecord<String, byte[]> record : records) {

                    T message;
                    try {
                        message = deserializer.deserialize(record.value());
                    } catch (DeserializationException e) {
                        onDeserializationError(record, e);
                        continue;
                    }

                    try {
                        // Calculate end-to-end latency (time from record timestamp to now)
                        long eventTimestamp = getEventTimestamp(message, record);
                        long endToEndLatency = System.currentTimeMillis() - eventTimestamp;

                        // Process the message and measure processing time
                        long processStartTime = System.currentTimeMillis();
                        process(message);
                        long processingTime = System.currentTimeMillis() - processStartTime;

                        // Record metrics
                        metrics.recordMessage(processingTime, endToEndLatency);

                    } catch (Exception e) {
                        onProcessingError(message, e);
                    }
                }

                if (!records.isEmpty()) {
                    consumer.commitSync();
                }
            }
        } catch (WakeupException e) {
            if (running.get()) {
                throw e;
            }
            // expected wakeup from shutdown()
        } finally {
            onStop();
            consumer.close();
            log.info("Consumer closed.");
        }
    }

    /** Trigger graceful shutdown. Safe to call from another thread. */
    public final void shutdown() {
        log.info("Shutdown requested.");
        running.set(false);
        if (consumer != null) {
            consumer.wakeup();
        }
    }

    /** For use in JVM shutdown hooks. */
    public final Thread shutdownHook() {
        return new Thread(this::shutdown, "consumer-shutdown-hook");
    }

    @Override
    public void close() {
        shutdown();
    }

    public boolean isRunning() {
        return running.get();
    }

    public ConsumerMetrics getMetrics() {
        return metrics;
    }

    public String getConsumerName() {
        return metrics.getConsumerName();
    }
}
