package com.stockstream.core.consumer;

import com.stockstream.core.config.ConsumerConfig;
import com.stockstream.core.model.Candle;

import java.util.function.Consumer;

public final class CandleConsumer extends AbstractKafkaConsumer<Candle> {

    private final Consumer<Candle> handler;

    public CandleConsumer(ConsumerConfig config, String topic, String groupId, Consumer<Candle> handler) {
        super(config, topic, groupId, new MessageDeserializer<>(Candle.class));
        if (handler == null) throw new IllegalArgumentException("handler is required");
        this.handler = handler;
    }

    @Override
    protected void process(Candle candle) {
        handler.accept(candle);
    }
}
