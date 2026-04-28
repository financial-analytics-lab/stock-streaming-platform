package com.stockstream.core.consumer;

import com.stockstream.core.config.ConsumerConfig;
import com.stockstream.core.model.Tick;

import java.util.function.Consumer;

/**
 * Concrete Kafka consumer for {@link Tick} messages on the tick topic.
 * The handler callback runs once per successfully deserialized tick.
 */
public final class TickConsumer extends AbstractKafkaConsumer<Tick> {

    private final Consumer<Tick> handler;

    public TickConsumer(ConsumerConfig config, String topic, String groupId, Consumer<Tick> handler) {
        super(config, topic, groupId, new MessageDeserializer<>(Tick.class));
        if (handler == null) throw new IllegalArgumentException("handler is required");
        this.handler = handler;
    }

    @Override
    protected void process(Tick tick) {
        handler.accept(tick);
    }
}
