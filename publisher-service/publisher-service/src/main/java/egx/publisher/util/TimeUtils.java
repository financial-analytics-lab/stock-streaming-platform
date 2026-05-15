package egx.publisher.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import egx.publisher.scheduler.SessionManager;




/**
 * Time utilities for parsing, formatting, and session rebasing of trade events.
 */
public final class TimeUtils {

    private static final Logger log = LoggerFactory.getLogger(TimeUtils.class);

    private static final DateTimeFormatter EXECUTION_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter LOG_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    .withZone(ZoneOffset.UTC);

    private static final Pattern AM_PM_PATTERN = Pattern.compile("\\s*[AP]M$", Pattern.CASE_INSENSITIVE);

    private TimeUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Parses a time string in format "MM/dd/yyyy HH:mm:ss a" (e.g., "02/01/2025 10:00:00 AM")
     * to an Instant in UTC.
     *
     * @param timeString the time string to parse
     * @return the corresponding Instant in UTC
     * @throws IllegalArgumentException if the format is invalid
     */
    public static Instant parseExecutionTime(String timeString) {
        if (timeString == null || timeString.trim().isEmpty()) {
            throw new IllegalArgumentException("Time string cannot be null or empty");
        }

        String normalized = normalizeAmPm(timeString.trim());
        try {
            return Instant.from(EXECUTION_TIME_FORMATTER.parse(normalized));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid time format: " + timeString, e);
        }
    }

    /**
     * Normalizes AM/PM to uppercase for consistent parsing.
     */
    private static String normalizeAmPm(String timeString) {
        return AM_PM_PATTERN.matcher(timeString).replaceFirst(match -> {
            String amPm = match.group().trim().toUpperCase();
            return " " + amPm;
        });
    }

    /**
     * Formats an Instant for logging purposes.
     *
     * @param instant the instant to format
     * @return formatted string in "yyyy-MM-dd HH:mm:ss.SSS" format
     */
    public static String formatForLogging(Instant instant) {
        if (instant == null) {
            return "null";
        }
        return LOG_FORMATTER.format(instant);
    }

    /**
     * Formats an Instant for display purposes using the execution time format.
     *
     * @param instant the instant to format
     * @return formatted string in "MM/dd/yyyy HH:mm:ss a" format
     */
    public static String formatExecutionTime(Instant instant) {
        if (instant == null) {
            return "null";
        }
        return EXECUTION_TIME_FORMATTER.format(instant);
    }

    /**
     * Computes session rebasing for a trade event.
     * Formula: due_i = H + A_s + offset_i
     * where:
     * - H is the horizon start time
     * - A_s is the session anchor (time since epoch for the session's first event)
     * - offset_i is the offset since the session's first event
     *
     * @param horizonStart the replay horizon start time
     * @param sessionAnchor the anchor time for this session (first event time in session)
     * @param eventTime the original event time
     * @return the calculated replay due time
     */
    public static Instant computeReplayDueTime(Instant horizonStart, Instant sessionAnchor, Instant eventTime) {
        if (horizonStart == null || sessionAnchor == null || eventTime == null) {
            throw new IllegalArgumentException("Parameters cannot be null");
        }

        long horizonMillis = horizonStart.toEpochMilli();
        long sessionAnchorMillis = sessionAnchor.toEpochMilli();
        long eventTimeMillis = eventTime.toEpochMilli();


        log.info("Computing replay due time - Horizon: {}, Session Anchor: {}, Event Time: {}",
                formatForLogging(horizonStart), formatForLogging(sessionAnchor), formatForLogging(eventTime));
        // due_i = H + (A_s - H) + (eventTime - sessionAnchor)
        // Simplified: due_i = H + (eventTime - sessionAnchor) + (A_s - H)
        // Further simplified: due_i = eventTime + (H - A_s)
        // But we want: due_i = H + (A_s - H) + (eventTime - sessionAnchor) = eventTime + (H - sessionAnchor)
        long dueTimeMillis = eventTimeMillis + (horizonMillis - sessionAnchorMillis);

        log.info("Computed replay due time: {}", formatForLogging(Instant.ofEpochMilli(dueTimeMillis)));

        return Instant.ofEpochMilli(dueTimeMillis);
    }

    /**
     * Calculates the session anchor (epoch time of the first event in the session).
     *
     * @param firstEventTime the time of the first event in the session
     * @return the session anchor
     */
    public static Instant computeSessionAnchor(Instant firstEventTime) {
        return firstEventTime;
    }

    /**
     * Extracts the session date from a date string in "MM/dd/yyyy" format.
     *
     * @param dateString the date string
     * @return the date string in "yyyy-MM-dd" format for use as session date
     */
    public static String extractSessionDate(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            throw new IllegalArgumentException("Date string cannot be null or empty");
        }

        try {
            DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
            DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            java.time.LocalDate date = java.time.LocalDate.parse(dateString.trim(), inputFormatter);
            return date.format(outputFormatter);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format: " + dateString, e);
        }
    }

    /**
     * Parses a date string in "MM/dd/yyyy" format to extract the date components.
     *
     * @param dateString the date string
     * @return array with [year, month, day]
     */
    public static int[] parseDateComponents(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            throw new IllegalArgumentException("Date string cannot be null or empty");
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
            java.time.LocalDate date = java.time.LocalDate.parse(dateString.trim(), formatter);
            return new int[]{date.getYear(), date.getMonthValue(), date.getDayOfMonth()};
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format: " + dateString, e);
        }
    }
}
