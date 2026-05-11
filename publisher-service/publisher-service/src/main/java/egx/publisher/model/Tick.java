package egx.publisher.model;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Public Tick POJO emitted to Kafka consumers (Java 11 compatible).
 */
public class Tick {
    private final long tickId;
    private final String symbol;
    private final BigDecimal price;
    private final long volume;
    private final Instant timestamp;
    private final Instant publishedAt;
    private final long lagMs;
    private final String securityName;  // Optional field for enriched data

    public Tick(@JsonProperty("tick_id") long tickId,
                @JsonProperty("symbol") String symbol,
                @JsonProperty("price") BigDecimal price,
                @JsonProperty("volume") long volume,
                @JsonProperty("timestamp") Instant timestamp,
                @JsonProperty("published_at") Instant publishedAt,
                @JsonProperty("lag_ms") long lagMs,
                @JsonProperty("security_name") String securityName) {
        this.tickId = tickId;
        this.symbol = symbol;
        this.price = price;
        this.volume = volume;
        this.timestamp = timestamp;
        this.publishedAt = publishedAt;
        this.lagMs = lagMs;
        this.securityName = securityName;
    }

    @JsonProperty("tick_id") public long getTickId() { return tickId; }
    @JsonProperty("symbol") public String getSymbol() { return symbol; }
    @JsonProperty("price") public BigDecimal getPrice() { return price; }
    @JsonProperty("volume") public long getVolume() { return volume; }
    @JsonProperty("timestamp") public Instant getTimestamp() { return timestamp; }
    @JsonProperty("published_at") public Instant getPublishedAt() { return publishedAt; }
    @JsonProperty("lag_ms") public long getLagMs() { return lagMs; }
    @JsonProperty("security_name") public String getSecurityName() { return securityName; }
}
