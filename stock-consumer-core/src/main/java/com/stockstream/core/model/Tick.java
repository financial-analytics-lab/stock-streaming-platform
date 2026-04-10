package com.stockstream.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

public record Tick(
        @JsonProperty("tick_id") long tickId,
        @JsonProperty("symbol") String symbol,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("volume") long volume,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("published_at") Instant publishedAt
) {
    public Tick {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be null or blank");
        }
        if (price == null) {
            throw new IllegalArgumentException("price must not be null");
        }
    }
}
