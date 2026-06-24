package com.stockstream.core.consumer;

import com.stockstream.core.config.ConsumerConfig;
import com.stockstream.core.metrics.ConsumerMetrics;
import com.stockstream.core.model.Tick;
import com.stockstream.core.storage.TickStorageWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class TickConsumerGroup implements AutoCloseable{
    private final List<TickConsumer> consumers = new ArrayList<>();
    private final ExecutorService executor;

    public TickConsumerGroup(ConsumerConfig config,
                             String topic,
                             String groupId,
                             int partitionCount,
                             Consumer<Tick> tickHandler,
                             TickStorageWriter storageWriter) {

        if (tickHandler == null) throw new IllegalArgumentException("tickHandler is required");
        if (partitionCount <= 0) throw new IllegalArgumentException("partitionCount must be > 0");

        // Initialize thread pool -- One executor thread per partition
        this.executor = Executors.newFixedThreadPool(partitionCount, r -> {
            Thread t = new Thread(r, "tick-consumer-" + consumers.size());
            t.setDaemon(true);
            return t;
        });

        for (int i = 0; i < partitionCount; i++) {
            final int partition = i;
            TickConsumer consumer = new TickConsumer(
                    config, topic, groupId, partition,
                    tick -> {
                        tickHandler.accept(tick);
                        if (storageWriter != null) {
                            storageWriter.add(tick);
                        }
                    }
            );
            consumers.add(consumer);
            executor.submit(consumer::start);
        }
    }

    public ConsumerMetrics getMetrics() {
        return consumers.isEmpty() ? null : consumers.get(0).getMetrics();
    }

    @Override
    public void close() {
        consumers.forEach(TickConsumer::shutdown);
        executor.shutdown();
    }
}