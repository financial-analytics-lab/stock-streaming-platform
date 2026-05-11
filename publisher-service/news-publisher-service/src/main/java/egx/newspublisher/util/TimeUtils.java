package egx.newspublisher.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public final class TimeUtils {
    private static final Logger log = LoggerFactory.getLogger(TimeUtils.class);

    private static final DateTimeFormatter NEWS_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private TimeUtils() {
    }

    public static Instant parseNewsDateTime(String dateTimeString) {
        if (dateTimeString == null || dateTimeString.trim().isEmpty()) {
            throw new IllegalArgumentException("Datetime string cannot be null or empty");
        }

        try {
            return Instant.from(NEWS_TIME_FORMATTER.parse(dateTimeString.trim()));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid news datetime format: " + dateTimeString, e);
        }
    }

    public static Instant computeReplayDueTime(Instant horizonStart, Instant sessionAnchor, Instant eventTime) {
        if (horizonStart == null || sessionAnchor == null || eventTime == null) {
            throw new IllegalArgumentException("Parameters cannot be null");
        }

        long horizonMillis = horizonStart.toEpochMilli();
        long sessionAnchorMillis = sessionAnchor.toEpochMilli();
        long eventTimeMillis = eventTime.toEpochMilli();
        log.info("Computing replay due time - Horizon: {}, Session Anchor: {}, Event Time: {}",
                horizonStart, sessionAnchor, eventTime);
        long dueTimeMillis = horizonMillis + (eventTimeMillis - sessionAnchorMillis);
        return Instant.ofEpochMilli(dueTimeMillis);
    }
}
