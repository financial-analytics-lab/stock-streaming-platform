package com.stockstream.candle.serializer;

import com.stockstream.candle.model.Candle;
import com.stockstream.core.model.Tick;
import org.apache.kafka.common.serialization.Serde;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonSerializerTest {

    @Test
    void tick_roundTrip() {
        Serde<Tick> serde = SerializerFactory.tickSerializer();
        Tick original = new Tick(1L, "AAPL", new BigDecimal("182.50"), 1000L,
                Instant.parse("2026-01-15T10:00:00Z"),
                Instant.parse("2026-01-15T10:00:00.100Z"));

        byte[] bytes = serde.serializer().serialize("topic", original);
        Tick restored = serde.deserializer().deserialize("topic", bytes);

        assertEquals(original.tickId(), restored.tickId());
        assertEquals(original.symbol(), restored.symbol());
        assertEquals(0, original.price().compareTo(restored.price()));
        assertEquals(original.volume(), restored.volume());
        assertEquals(original.timestamp(), restored.timestamp());
        assertEquals(original.publishedAt(), restored.publishedAt());
    }

    @Test
    void candle_roundTrip() {
        Serde<Candle> serde = SerializerFactory.candleSerializer();
        Instant start = Instant.parse("2026-01-15T10:00:00Z");
        Candle original = new Candle(
                "MSFT", "1m",
                start, start.plusSeconds(60),
                new BigDecimal("415.00"), new BigDecimal("416.50"),
                new BigDecimal("414.80"), new BigDecimal("416.00"),
                25000L, 180L,
                Instant.parse("2026-01-15T10:01:10Z")
        );

        byte[] bytes = serde.serializer().serialize("topic", original);
        Candle restored = serde.deserializer().deserialize("topic", bytes);

        assertEquals(original.symbol(), restored.symbol());
        assertEquals(original.interval(), restored.interval());
        assertEquals(original.windowStart(), restored.windowStart());
        assertEquals(original.windowEnd(), restored.windowEnd());
        assertEquals(0, original.open().compareTo(restored.open()));
        assertEquals(0, original.high().compareTo(restored.high()));
        assertEquals(0, original.low().compareTo(restored.low()));
        assertEquals(0, original.close().compareTo(restored.close()));
        assertEquals(original.volume(), restored.volume());
        assertEquals(original.tickCount(), restored.tickCount());
    }

    @Test
    void null_serializesAndDeserializesToNull() {
        Serde<Tick> serde = SerializerFactory.tickSerializer();
        assertNull(serde.serializer().serialize("topic", null));
        assertNull(serde.deserializer().deserialize("topic", null));
    }
}
