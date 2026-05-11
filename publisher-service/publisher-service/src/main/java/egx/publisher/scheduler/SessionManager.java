package egx.publisher.scheduler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import egx.publisher.config.AppConfig;
import egx.publisher.model.SessionShard;
import egx.publisher.model.TradeEvent;
import egx.publisher.util.TimeUtils;

/**
 * Manages loading and processing of session shards from CSV files.
 * Applies session rebasing to compute replay due times.
 */
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    private final AppConfig.ReplayConfig replayConfig;
    private final AppConfig.SymbolsConfig symbolsConfig;
    private final Map<String, String> symbolMapping;

    private static final DateTimeFormatter SESSION_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter SESSION_INSTANT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    // CSV column indices (header: event_time_original,symbol_code,security_name,session_id,sequence_id,transaction_id,price,volume,open,close,type)
    private static final int COL_EVENT_TIME_ORIGINAL = 0;
    private static final int COL_SYMBOL_CODE = 1;
    private static final int COL_SECURITY_NAME = 2;
    private static final int COL_SESSION_ID = 3;
    private static final int COL_SEQUENCE_ID = 4;
    private static final int COL_TRANSACTION_ID = 5;
    private static final int COL_PRICE = 6;
    private static final int COL_VOLUME = 7;
    private static final int COL_OPEN = 8;
    private static final int COL_CLOSE = 9;
    private static final int COL_TYPE = 10;

    public SessionManager(AppConfig.ReplayConfig replayConfig, AppConfig.SymbolsConfig symbolsConfig) {
        this.replayConfig = replayConfig;
        this.symbolsConfig = symbolsConfig;
        this.symbolMapping = loadSymbolMapping();
        log.info("SessionManager initialized with artifact path: {}", replayConfig.getArtifactPath());
    }

    /**
     * Loads the symbol code to name mapping from the configured JSON file.
     */
    private Map<String, String> loadSymbolMapping() {
        Map<String, String> mapping = new HashMap<>();
        if (symbolsConfig != null && symbolsConfig.getMappingFile() != null) {
            try {
                String content = Files.readString(java.nio.file.Path.of(symbolsConfig.getMappingFile()));
                // Simple JSON parsing: {"key": "value", ...}
                String[] pairs = content.replace("{", "").replace("}", "").replace("\"", "").split(",");
                for (String pair : pairs) {
                    String[] kv = pair.trim().split(":");
                    if (kv.length == 2) {
                        mapping.put(kv[0].trim(), kv[1].trim());
                    }
                }
                log.info("Loaded {} symbol mappings from {}", mapping.size(), symbolsConfig.getMappingFile());
            } catch (IOException e) {
                log.warn("Could not load symbol mapping from {}: {}", symbolsConfig.getMappingFile(), e.getMessage());
            }
        }
        return mapping;
    }

    /**
     * Loads all session shards from the artifact path.
     *
     * @return list of discovered session shards sorted by date
     */
    public List<SessionShard> loadSessionShards() {
        List<SessionShard> shards = new ArrayList<>();
        String artifactPath = replayConfig.getArtifactPath();

        File baseDir = new File(artifactPath);
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            log.warn("Artifact directory does not exist: {}", artifactPath);
            return shards;
        }

        // Find all session_date=YYYYMMDD directories
        File[] sessionDirs = baseDir.listFiles((dir, name) -> name.startsWith("session_date="));
        if (sessionDirs == null) {
            log.warn("No session directories found in {}", artifactPath);
            return shards;
        }

        for (File sessionDir : sessionDirs) {
            String dateStr = sessionDir.getName().replace("session_date=", "");

            // Count CSV files in this directory
            File[] csvFiles = sessionDir.listFiles((dir, name) -> name.endsWith(".csv"));
            if (csvFiles != null && csvFiles.length > 0) {
                shards.add(new SessionShard(dateStr, sessionDir.getAbsolutePath()));
                log.debug("Found shard for session date: {}", dateStr);
            }
        }

        shards.sort(Comparator.comparing(SessionShard::getSessionDate));
        log.info("Loaded {} session shards from {}", shards.size(), artifactPath);
        return shards;
    }

    /**
     * Loads all trade events from shards with session dates >= the given horizon.
     *
     * @param horizonStart the instant from which to load events
     * @return list of trade events with computed replay due times
     */
    public List<TradeEvent> loadEventsFromHorizon(Instant horizonStart) {
        List<SessionShard> shards = loadSessionShards();
        List<TradeEvent> allEvents = new ArrayList<>();

        // Filter shards to only those starting from horizon
        String horizonDateStr = SESSION_DATE_FORMATTER.format(horizonStart);

        for (SessionShard shard : shards) {
            if (shard.getSessionDate().compareTo(horizonDateStr) >= 0) {
                log.info("Loading events from session: {}", shard.getSessionDate());
                List<TradeEvent> events = loadShardEvents(shard, horizonStart);
                allEvents.addAll(events);
                log.info("Loaded {} events from session {}", events.size(), shard.getSessionDate());
            }
        }

        // Sort all events by replay due time
        allEvents.sort(Comparator.comparing(TradeEvent::getReplayDueTime));
        log.info("Total events loaded for replay: {}", allEvents.size());
        return allEvents;
    }

    /**
     * Loads all trade events from a single session by date.
     * The horizon start is automatically set to midnight of the session date.
     *
     * @param sessionDateStr session date as "yyyyMMdd", e.g. "20250122"
     * @return list of trade events with computed replay due times
     */
    public List<TradeEvent> loadSingleSession(String sessionDateStr) {
        log.info("Loading single session: {}", sessionDateStr);

        File baseDir = new File(replayConfig.getArtifactPath());
        File sessionDir = new File(baseDir, "session_date=" + sessionDateStr);

        if (!sessionDir.exists() || !sessionDir.isDirectory()) {
            log.warn("Session directory not found: {}", sessionDir.getAbsolutePath());
            return Collections.emptyList();
        }

        SessionShard shard = new SessionShard(sessionDateStr, sessionDir.getAbsolutePath());
        Instant horizonStart = Instant.from(SESSION_INSTANT_FORMATTER.parse(sessionDateStr + "T10:00:00Z"));

        List<TradeEvent> events = loadShardEvents(shard, horizonStart);
        events.sort(Comparator.comparing(TradeEvent::getReplayDueTime));
        log.info("Loaded {} events from single session {}", events.size(), sessionDateStr);
        return events;
    }

    /**
     * Loads events from a single shard and applies session rebasing.
     *
     * @param shard the session shard to load
     * @param horizonStart the replay horizon start
     * @return list of trade events with computed replay due times
     */
    private List<TradeEvent> loadShardEvents(SessionShard shard, Instant horizonStart) {
        List<TradeEvent> events = new ArrayList<>();
        File shardDir = shard.getFilePath().toFile();

        File[] csvFiles = shardDir.listFiles((dir, name) -> name.endsWith(".csv"));
        if (csvFiles == null || csvFiles.length == 0) {
            log.warn("No CSV files found in shard directory: {}", shard.getFilePath());
            return events;
        }

        try {
            List<String[]> rawRecords = new ArrayList<>();

            // Read all CSV files in the shard directory
            for (File csvFile : csvFiles) {
                try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
                    String line = reader.readLine(); // Skip header

                    while ((line = reader.readLine()) != null) {
                        if (!line.trim().isEmpty()) {
                            rawRecords.add(parseCSVLine(line));
                        }
                    }
                }
            }

            if (rawRecords.isEmpty()) {
                log.warn("No valid records found in shard {}", shard.getSessionDate());
                return events;
            }

            // Find session anchor (earliest event time in this session)
            Instant sessionAnchor = null;
            for (String[] record : rawRecords) {
                try {
                    String datetimeStr = record[COL_EVENT_TIME_ORIGINAL];
                    Instant eventTime = TimeUtils.parseExecutionTime(datetimeStr);
                    if (sessionAnchor == null || eventTime.isBefore(sessionAnchor)) {
                        log.debug("Found Anchor event time in shard {}: {}", shard.getSessionDate(), eventTime);
                        sessionAnchor = eventTime;
                    }
                } catch (Exception e) {
                    // Skip malformed records
                    log.debug("Could not parse datetime from record: {}", e.getMessage());
                }
            }

            if (sessionAnchor == null) {
                log.warn("No valid events found in shard {}", shard.getSessionDate());
                return events;
            }

            log.debug("Session anchor for {}: {}", shard.getSessionDate(), sessionAnchor);

            // Build trade events with replay due times
            long sequenceId = 0;
            for (String[] record : rawRecords) {
                try {
                    sequenceId++;
                    String datetimeStr = record[COL_EVENT_TIME_ORIGINAL];
                    String symbolCode = record[COL_SYMBOL_CODE];
                    String securityName = record[COL_SECURITY_NAME];
                    String sessionId = record[COL_SESSION_ID];
                    // session_date is in the directory path, not in the CSV
                    String sessionDate = shard.getSessionDate();
                    String transactionId = record[COL_TRANSACTION_ID];
                    double price = Double.parseDouble(record[COL_PRICE]);
                    long volume = Long.parseLong(record[COL_VOLUME]);

                    Instant eventTime = TimeUtils.parseExecutionTime(datetimeStr);

                    // Compute replay due time using session rebasing
                    Instant replayDueTime = TimeUtils.computeReplayDueTime(
                            horizonStart, sessionAnchor, eventTime);

                    TradeEvent event = new TradeEvent.Builder()
                            .eventTimeOriginal(eventTime)
                            .symbolCode(symbolCode)
                            .securityName(securityName)
                            .sessionId(sessionId)
                            .sessionDate(sessionDate)
                            .sequenceId(sequenceId)
                            .tradePrice(price)
                            .volumeTraded(volume)
                            .ticketId(transactionId)
                            .replayDueTime(replayDueTime)
                            .build();

                    events.add(event);

                    //Thread.sleep(500);
                } catch (Exception e) {
                    log.warn("Error processing record in shard {}: {}",
                            shard.getSessionDate(), e.getMessage());
                }
            }

        } catch (IOException e) {
            log.error("Failed to read CSV file for session {}: {}",
                    shard.getSessionDate(), e.getMessage());
        }

        return events;
    }

    /**
     * Parses a CSV line handling quoted fields with commas.
     */
    private String[] parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(field.toString().trim());
                field = new StringBuilder();
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString().trim());

        return fields.toArray(new String[0]);
    }
}