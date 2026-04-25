package com.stockstream.core.consumer;

import com.stockstream.core.exception.DeserializationException;
import com.stockstream.core.model.Tick;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TickDeserializerTest {

    @Test
    void deserializesValidJson() {
        String json = """
                {
                  "tick_id": 1,
                  "symbol": "AAPL",
                  "price": 150.25,
                  "volume": 1000,
                  "timestamp": "2025-01-15T10:30:00Z",
                  "published_at": "2025-01-15T10:30:01Z"
                }
                """;

        Tick tick = TickDeserializer.deserialize(json.getBytes(StandardCharsets.UTF_8));

        assertEquals(1L, tick.tickId());
        assertEquals("AAPL", tick.symbol());
        assertEquals(new BigDecimal("150.25"), tick.price());
        assertEquals(1000L, tick.volume());
        assertNotNull(tick.timestamp());
        assertNotNull(tick.publishedAt());
    }

    @Test
    void throwsOnMalformedJson() {
        byte[] garbage = "not json".getBytes(StandardCharsets.UTF_8);

        assertThrows(DeserializationException.class, () ->
                TickDeserializer.deserialize(garbage));
    }

    @Test
    void throwsOnEmptyBytes() {
        assertThrows(DeserializationException.class, () ->
                TickDeserializer.deserialize(new byte[0]));
    }

    @Test
    void throwsWhenRequiredFieldMissing() {
        // symbol is required by the Tick record's compact constructor
        String json = """
                {
                  "tick_id": 1,
                  "price": 100.0,
                  "volume": 500,
                  "timestamp": "2025-01-15T10:30:00Z"
                }
                """;

        assertThrows(DeserializationException.class, () ->
                TickDeserializer.deserialize(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void ignoresUnknownFields() {
        String json = """
                {
                  "tick_id": 2,
                  "symbol": "GOOG",
                  "price": 2800.00,
                  "volume": 200,
                  "timestamp": "2025-01-15T11:00:00Z",
                  "published_at": "2025-01-15T11:00:01Z",
                  "extra_field": "should be ignored"
                }
                """;

        Tick tick = TickDeserializer.deserialize(json.getBytes(StandardCharsets.UTF_8));
        assertEquals("GOOG", tick.symbol());
    }
}
