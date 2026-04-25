package com.stockstream.core.consumer;

import com.stockstream.core.config.ConsumerConfig;
import com.stockstream.core.model.NewsEvent;

import java.util.function.Consumer;

/**
 * Concrete Kafka consumer for {@link NewsEvent} messages on the news topic.
 * The handler callback runs once per successfully deserialized event.
 */
public final class NewsConsumer extends AbstractKafkaConsumer<NewsEvent> {

    private final Consumer<NewsEvent> handler;

    public NewsConsumer(ConsumerConfig config, String topic, String groupId, Consumer<NewsEvent> handler) {
        super(config, topic, groupId, new MessageDeserializer<>(NewsEvent.class));
        if (handler == null) throw new IllegalArgumentException("handler is required");
        this.handler = handler;
    }

    @Override
    protected void process(NewsEvent event) {
        handler.accept(event);
    }
}
