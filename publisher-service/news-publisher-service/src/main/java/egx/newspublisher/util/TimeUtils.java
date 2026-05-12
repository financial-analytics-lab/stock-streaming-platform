package egx.newspublisher.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public final class TimeUtils {
    private static final Logger log = LoggerFactory.getLogger(TimeUtils.class);

        // Many news timestamps are provided in a local source timezone (e.g. UTC+12).
        // Interpret incoming news datetime strings using the source offset so Instants are correct.
        private static final int NEWS_SOURCE_OFFSET_HOURS = 12;
        private static final DateTimeFormatter NEWS_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TimeUtils() {
    }

    public static Instant parseNewsDateTime(String dateTimeString) {
        if (dateTimeString == null || dateTimeString.trim().isEmpty()) {
            throw new IllegalArgumentException("Datetime string cannot be null or empty");
        }

        try {
            // Parse as local date-time in the source timezone, then convert to Instant
            java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(dateTimeString.trim(), NEWS_TIME_FORMATTER);
            return ldt.toInstant(ZoneOffset.ofHours(NEWS_SOURCE_OFFSET_HOURS));
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
        long dueTimeMillis = eventTimeMillis + (horizonMillis - sessionAnchorMillis);
            return Instant.ofEpochMilli(dueTimeMillis);
    }
}
