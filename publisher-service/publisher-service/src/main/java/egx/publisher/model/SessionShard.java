package egx.publisher.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a session's Parquet shard containing trade events.
 */
public class SessionShard {

    private final String sessionDate;
    private final Path filePath;
    private final long eventCount;
    private final List<TradeEvent> tradeEvents;

    public SessionShard(String sessionDate, Path filePath) {
        this.sessionDate = sessionDate;
        this.filePath = filePath;
        this.eventCount = 0;
        this.tradeEvents = new ArrayList<>();
    }

    public SessionShard(String sessionDate, String filePathString) {
        this.sessionDate = sessionDate;
        this.filePath = java.nio.file.Paths.get(filePathString);
        this.eventCount = 0;
        this.tradeEvents = new ArrayList<>();
    }

    public SessionShard(String sessionDate, Path filePath, long eventCount, List<TradeEvent> tradeEvents) {
        this.sessionDate = sessionDate;
        this.filePath = filePath;
        this.eventCount = eventCount;
        this.tradeEvents = new ArrayList<>(tradeEvents);
    }

    public String getSessionDate() {
        return sessionDate;
    }

    public Path getFilePath() {
        return filePath;
    }

    public long getEventCount() {
        return eventCount;
    }

    public List<TradeEvent> getTradeEvents() {
        return new ArrayList<>(tradeEvents);
    }

    public void setTradeEvents(List<TradeEvent> tradeEvents) {
        this.tradeEvents.clear();
        this.tradeEvents.addAll(tradeEvents);
    }

    @Override
    public String toString() {
        return "SessionShard{" +
                "sessionDate='" + sessionDate + '\'' +
                ", filePath=" + filePath +
                ", eventCount=" + eventCount +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SessionShard that = (SessionShard) o;
        return Objects.equals(sessionDate, that.sessionDate) && Objects.equals(filePath, that.filePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionDate, filePath);
    }
}