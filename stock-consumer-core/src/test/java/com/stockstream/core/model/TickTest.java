package com.stockstream.core.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TickTest {

    @Test
    void validTickCreation() {
        Tick tick = new Tick(1L, "AAPL", new BigDecimal("150.00"), 1000L,
                Instant.now(), Instant.now());

        assertEquals(1L, tick.tickId());
        assertEquals("AAPL", tick.symbol());
    }

    @Test
    void rejectsNullSymbol() {
        assertThrows(IllegalArgumentException.class, () ->
                new Tick(1L, null, new BigDecimal("100"), 500L, Instant.now(), Instant.now()));
    }

    @Test
    void rejectsBlankSymbol() {
        assertThrows(IllegalArgumentException.class, () ->
                new Tick(1L, "  ", new BigDecimal("100"), 500L, Instant.now(), Instant.now()));
    }

    @Test
    void rejectsNullPrice() {
        assertThrows(IllegalArgumentException.class, () ->
                new Tick(1L, "AAPL", null, 500L, Instant.now(), Instant.now()));
    }
}
