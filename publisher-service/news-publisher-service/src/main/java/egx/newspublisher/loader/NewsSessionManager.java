package egx.newspublisher.loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import egx.newspublisher.model.NewsEvent;
import egx.newspublisher.util.TimeUtils;

public class NewsSessionManager {

    private static final Logger log = LoggerFactory.getLogger(NewsSessionManager.class);
    private static final DateTimeFormatter SESSION_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Path newsPath;
    private final ObjectMapper objectMapper;

    public NewsSessionManager(String newsFilePath) {
        this.newsPath = Path.of(newsFilePath);
        this.objectMapper = new ObjectMapper();
        log.info("NewsSessionManager initialized with news file: {}", this.newsPath.toAbsolutePath());
    }

    public List<NewsEvent> loadEventsFromHorizon(Instant horizonStart) {
        String horizonDate = SESSION_DATE_FORMATTER.withZone(ZoneOffset.UTC).format(horizonStart);
        return loadEvents(date -> date.compareTo(horizonDate) >= 0, horizonStart);
    }

    public List<NewsEvent> loadSingleDate(String sessionDateStr) {
        Instant horizonStart = LocalDate.parse(sessionDateStr, SESSION_DATE_FORMATTER)
                .atStartOfDay(ZoneOffset.UTC)
                .plusHours(10)
                .toInstant();
        return loadEvents(date -> date.equals(sessionDateStr), horizonStart);
    }

    private List<NewsEvent> loadEvents(DateFilter dateFilter, Instant horizonStart) {
        List<NewsEvent> allEvents = new ArrayList<>();

        if (!Files.exists(newsPath)) {
            log.warn("News file does not exist: {}", newsPath.toAbsolutePath());
            return allEvents;
        }

        try {
            JsonNode root = objectMapper.readTree(newsPath.toFile());
            JsonNode byDate = root.path("by_date");
            if (byDate.isMissingNode() || !byDate.isObject()) {
                log.warn("News file does not contain a valid by_date object: {}", newsPath.toAbsolutePath());
                return allEvents;
            }

            Iterator<Map.Entry<String, JsonNode>> dateIterator = byDate.fields();
            while (dateIterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = dateIterator.next();
                String sessionDate = entry.getKey();
                if (!dateFilter.matches(sessionDate)) {
                    continue;
                }

                JsonNode articles = entry.getValue();
                if (!articles.isArray() || articles.isEmpty()) {
                    continue;
                }

                List<JsonNode> rawArticles = new ArrayList<>();
                for (JsonNode node : articles) {
                    rawArticles.add(node);
                }

                Instant sessionAnchor = findSessionAnchor(rawArticles);
                if (sessionAnchor == null) {
                    log.warn("Skipping news date {} because no valid datetime values were found", sessionDate);
                    continue;
                }

                for (JsonNode node : rawArticles) {
                    try {
                        NewsEvent event = objectMapper.treeToValue(node, NewsEvent.class);
                        Instant eventTime = resolveEventTime(event);
                        Instant replayDueTime = TimeUtils.computeReplayDueTime(horizonStart, sessionAnchor, eventTime);
                        event.setReplayDueTime(replayDueTime);
                        allEvents.add(event);
                    } catch (Exception e) {
                        log.warn("Failed to map news article for date {}: {}", sessionDate, e.getMessage());
                    }
                }
            }

            allEvents.sort(Comparator.naturalOrder());
            log.info("Loaded {} news events from {}", allEvents.size(), newsPath.toAbsolutePath());
            return allEvents;
        } catch (IOException e) {
            log.error("Failed to read news JSON from {}: {}", newsPath.toAbsolutePath(), e.getMessage(), e);
            return allEvents;
        }
    }

    private Instant findSessionAnchor(List<JsonNode> rawArticles) {
        Instant sessionAnchor = null;
        for (JsonNode node : rawArticles) {
            try {
                String datetime = node.path("datetime").asText(null);
                Instant eventTime = TimeUtils.parseNewsDateTime(datetime);
                if (sessionAnchor == null || eventTime.isBefore(sessionAnchor)) {
                    sessionAnchor = eventTime;
                }
            } catch (Exception e) {
                log.debug("Skipping malformed datetime while finding session anchor: {}", e.getMessage());
            }
        }
        return sessionAnchor;
    }

    private Instant resolveEventTime(NewsEvent event) {
        if (event.getDatetime() != null && !event.getDatetime().isBlank()) {
            return TimeUtils.parseNewsDateTime(event.getDatetime());
        }
        if (event.getDate() != null && event.getTime() != null) {
            return TimeUtils.parseNewsDateTime(event.getDate() + " " + event.getTime());
        }
        throw new IllegalArgumentException("News event missing datetime/date+time values");
    }

    @FunctionalInterface
    private interface DateFilter {
        boolean matches(String sessionDate);
    }
}
