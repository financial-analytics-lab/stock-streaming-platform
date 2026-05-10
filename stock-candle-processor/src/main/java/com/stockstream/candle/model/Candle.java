package com.stockstream.candle.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

public record Candle(
        @JsonProperty("symbol")       String symbol,
        @JsonProperty("interval")     String interval,
        @JsonProperty("window_start") Instant windowStart,
        @JsonProperty("window_end")   Instant windowEnd,
        @JsonProperty("open")         BigDecimal open,
        @JsonProperty("high")         BigDecimal high,
        @JsonProperty("low")          BigDecimal low,
        @JsonProperty("close")        BigDecimal close,
        @JsonProperty("volume")       long volume,
        @JsonProperty("tick_count")   long tickCount,
        @JsonProperty("published_at") Instant publishedAt,
        @JsonProperty("status")       CandleStatus status
) {
    public Candle {
        if (symbol != null && symbol.isBlank())
            throw new IllegalArgumentException("symbol must not be blank");
        if (volume < 0)
            throw new IllegalArgumentException("volume cannot be negative");
    }
}
