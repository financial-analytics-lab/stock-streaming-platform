package com.stockstream.candle.serializer;

import com.stockstream.candle.model.Candle;
import com.stockstream.core.model.Tick;
import org.apache.kafka.common.serialization.Serde;

public final class SerializerFactory {

    private SerializerFactory() {}

    public static Serde<Tick> tickSerializer() {
        return new JsonSerializer<>(Tick.class);
    }

    public static Serde<Candle> candleSerializer() {
        return new JsonSerializer<>(Candle.class);
    }
}
