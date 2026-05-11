package egx.publisher.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Canonical trade event model representing a single market trade.
 */
public class TradeEvent implements Comparable<TradeEvent> {

/**
 * 
 * 
 * public record Tick(
        @JsonProperty("tick_id") long tickId,
        @JsonProperty("symbol") String symbol,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("volume") long volume,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("published_at") Instant publishedAt
) {
 */

    private Instant eventTimeOriginal;
    private String symbolCode;
    private String securityName;
    private String sessionId;
    private String sessionDate;
    private Long sequenceId;
    private Double tradePrice;
    private Long volumeTraded;
    private String ticketId;
    private Instant replayDueTime;
    private long lagMs; 

    public TradeEvent() {}

    private TradeEvent(Builder builder) {
        this.eventTimeOriginal = builder.eventTimeOriginal;
        this.symbolCode = builder.symbolCode;
        this.securityName = builder.securityName;
        this.sessionId = builder.sessionId;
        this.sessionDate = builder.sessionDate;
        this.sequenceId = builder.sequenceId;
        this.tradePrice = builder.tradePrice;
        this.volumeTraded = builder.volumeTraded;
        this.ticketId = builder.ticketId;
        this.replayDueTime = builder.replayDueTime;
        this.lagMs = 0L;  
    }

    // ─── Getters ───────────────────────────────────────────────────────────────

    public Instant getEventTimeOriginal() { return eventTimeOriginal; }
    public String getSymbolCode() { return symbolCode; }
    public String getSecurityName() { return securityName; }
    public String getSessionId() { return sessionId; }
    public String getSessionDate() { return sessionDate; }
    public Long getSequenceId() { return sequenceId; }
    public Double getTradePrice() { return tradePrice; }
    public Long getVolumeTraded() { return volumeTraded; }
    public String getTicketId() { return ticketId; }

    public Instant getReplayDueTime() {
        return replayDueTime;
    }
    public long getLagMs() {
        return lagMs;
    }

    // ─── Setters ───────────────────────────────────────────────────────────────

    public void setEventTimeOriginal(Instant eventTimeOriginal) { this.eventTimeOriginal = eventTimeOriginal; }
    public void setSymbolCode(String symbolCode) { this.symbolCode = symbolCode; }
    public void setSecurityName(String securityName) { this.securityName = securityName; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public void setSessionDate(String sessionDate) { this.sessionDate = sessionDate; }
    public void setSequenceId(Long sequenceId) { this.sequenceId = sequenceId; }
    public void setTradePrice(Double tradePrice) { this.tradePrice = tradePrice; }
    public void setVolumeTraded(Long volumeTraded) { this.volumeTraded = volumeTraded; }
    public void setTicketId(String ticketId) { this.ticketId = ticketId; }

    public void setReplayDueTime(Instant replayDueTime) {
        this.replayDueTime = replayDueTime;
    }
    public void setLagMs(long lagMs) {
        this.lagMs = lagMs;
    }

    /**
     * Natural ordering by replayDueTime for the priority queue.
     */
    @Override
    public int compareTo(TradeEvent other) {
        if (this.replayDueTime == null && other.replayDueTime == null) return 0;
        if (this.replayDueTime == null) return -1;
        if (other.replayDueTime == null) return 1;
        return this.replayDueTime.compareTo(other.replayDueTime);
    }

    @Override
    public String toString() {
        return "TradeEvent{" +
                "eventTimeOriginal=" + eventTimeOriginal +
                ", symbolCode='" + symbolCode + '\'' +
                ", securityName='" + securityName + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", sessionDate='" + sessionDate + '\'' +
                ", sequenceId=" + sequenceId +
                ", tradePrice=" + tradePrice +
                ", volumeTraded=" + volumeTraded +
                ", ticketId='" + ticketId + '\'' +
                ", replayDueTime=" + replayDueTime +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TradeEvent that = (TradeEvent) o;
        return Objects.equals(ticketId, that.ticketId) && Objects.equals(sequenceId, that.sequenceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ticketId, sequenceId);
    }

    /**
     * Convert this TradeEvent into the public Tick shape expected by subscribers.
     * The caller supplies the {@code publishedAt} timestamp which will be
     * included as `published_at` in the emitted JSON.
     */
    public Tick toTick(Instant publishedAt) {
        long tickId = 0L;
        if (this.sequenceId != null) tickId = this.sequenceId.longValue();
        else if (this.ticketId != null) {
            try {
                tickId = Long.parseLong(this.ticketId);
            } catch (NumberFormatException ignored) {
                tickId = this.ticketId.hashCode();
            }
        }

        BigDecimal price = this.tradePrice != null ? BigDecimal.valueOf(this.tradePrice) : BigDecimal.ZERO;
        long volume = this.volumeTraded != null ? this.volumeTraded.longValue() : 0L;
        Instant timestamp = this.eventTimeOriginal != null ? this.eventTimeOriginal : Instant.EPOCH;

        return new Tick(tickId, this.symbolCode, price, volume, timestamp, publishedAt, this.lagMs, this.securityName);
    }

    public static class Builder {
        private Instant eventTimeOriginal;
        private String symbolCode;
        private String securityName;
        private String sessionId;
        private String sessionDate;
        private Long sequenceId;
        private Double tradePrice;
        private Long volumeTraded;
        private String ticketId;
        private Instant replayDueTime;
        private long lagMs;
        public Builder eventTimeOriginal(Instant eventTimeOriginal) {
            this.eventTimeOriginal = eventTimeOriginal;
            return this;
        }

        public Builder symbolCode(String symbolCode) {
            this.symbolCode = symbolCode;
            return this;
        }

        public Builder securityName(String securityName) {
            this.securityName = securityName;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder sessionDate(String sessionDate) {
            this.sessionDate = sessionDate;
            return this;
        }

        public Builder sequenceId(Long sequenceId) {
            this.sequenceId = sequenceId;
            return this;
        }

        public Builder tradePrice(Double tradePrice) {
            this.tradePrice = tradePrice;
            return this;
        }

        public Builder volumeTraded(Long volumeTraded) {
            this.volumeTraded = volumeTraded;
            return this;
        }

        public Builder ticketId(String ticketId) {
            this.ticketId = ticketId;
            return this;
        }

        public Builder replayDueTime(Instant replayDueTime) {
            this.replayDueTime = replayDueTime;
            return this;
        }

        public Builder lagMs(long lagMs) {
            this.lagMs = lagMs;
            return this;
        }

        public TradeEvent build() {
            return new TradeEvent(this);
        }
    }
}