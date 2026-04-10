package com.stockstream.core.consumer;

import com.stockstream.core.config.ConsumerConfig;
import com.stockstream.core.exception.DeserializationException;
import com.stockstream.core.logging.LoggerFactory;
import com.stockstream.core.model.Tick;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractTickConsumer implements AutoCloseable {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final ConsumerConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private KafkaConsumer<String, byte[]> consumer;

    protected AbstractTickConsumer(ConsumerConfig config) {
        this.config = config;
    }


    /** Process a single deserialized tick. Called once per record. */
    protected abstract void process(Tick tick);

    /**
     * Called when a record fails deserialization.
     * Default: log and skip. Override to send to dead-letter topic, etc.
     */
    protected void onDeserializationError(ConsumerRecord<String, byte[]> record, DeserializationException e) {
        log.log(Level.WARNING, "Skipping undeserializable record at offset " +
                record.offset() + " partition " + record.partition(), e);
    }

    /**
     * Called when process(Tick) throws.
     * Default: log and skip. Override for retry logic, DLQ routing, etc.
     */
    protected void onProcessingError(Tick tick, Exception e) {
        log.log(Level.SEVERE, "Error processing tick " + tick.tickId() +
                " [" + tick.symbol() + "]", e);
    }

    /**
     * Called once before the poll loop starts, after subscription.
     * Override for one-time setup (DB connections, etc.).
     */
    protected void onStart() {}

    /**
     * Called once after the poll loop exits, before consumer.close().
     * Override for cleanup.
     */
    protected void onStop() {}

    // ---- Lifecycle ----

    /** Start the poll loop. Blocks the calling thread. */
    public final void start() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Consumer is already running");
        }

        consumer = new KafkaConsumer<>(config.toKafkaProperties());
        consumer.subscribe(Collections.singletonList(config.topic()));
        log.info("Subscribed to topic '" + config.topic() + "' with group '" + config.groupId() + "'");

        onStart();

        try {
            while (running.get()) {
                ConsumerRecords<String, byte[]> records = consumer.poll(config.pollTimeout());

                for (ConsumerRecord<String, byte[]> record : records) {
                    Tick tick;
                    try {
                        tick = TickDeserializer.deserialize(record.value());
                    } catch (DeserializationException e) {
                        onDeserializationError(record, e);
                        continue;
                    }

                    try {
                        process(tick);
                    } catch (Exception e) {
                        onProcessingError(tick, e);
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

    /** Trigger graceful shutdown. Safe to call from another thread (e.g., shutdown hook). */
    public final void shutdown() {
        log.info("Shutdown requested.");
        running.set(false);
        if (consumer != null) {
            consumer.wakeup();
        }
    }

    /** For use in shutdown hooks. */
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
}
