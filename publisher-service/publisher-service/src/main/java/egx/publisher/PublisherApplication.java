package egx.publisher;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import egx.publisher.config.AppConfig;
import egx.publisher.kafka.KafkaPublisher;
import egx.publisher.model.TradeEvent;
import egx.publisher.scheduler.DeterministicScheduler;
import egx.publisher.scheduler.SessionManager;

/**
 * Main entry point for the EGX Publisher Service.
 * Loads configuration, initializes components, and runs the replay loop.
 */
public class PublisherApplication {

    private static final Logger log = LoggerFactory.getLogger(PublisherApplication.class);

    public static void main(String[] args) {
        log.info("Starting EGX Publisher Service");

        // Determine config path: use command-line arg or default to config/application.yaml
        String configPath = args.length > 0 ? args[0] : "config/application.yaml";

        // Load configuration
        AppConfig config;
        try {
            config = AppConfig.load(configPath);
            log.info("Configuration loaded successfully from {}", configPath);
        } catch (Exception e) {
            log.error("Failed to load configuration from {}: {}", configPath, e.getMessage(), e);
            System.exit(1);
            return;
        }

        // Initialize Kafka publisher
        KafkaPublisher kafkaPublisher;
        try {
            kafkaPublisher = new KafkaPublisher(config.getKafka());
            log.info("Kafka publisher initialized, bootstrap servers: {}", config.getKafka().getBootstrapServers());
        } catch (Exception e) {
            log.error("Failed to initialize Kafka publisher: {}", e.getMessage(), e);
            System.exit(1);
            return;
        }

        // Initialize session manager
        SessionManager sessionManager = new SessionManager(config.getReplay(), config.getSymbols());

        // Load events — either single session or from horizon
        List<TradeEvent> allEvents;
        Instant horizonStart;

        String singleSessionDate = config.getReplay().getSingleSessionDate();
        if (singleSessionDate != null && !singleSessionDate.isBlank()) {
            // Single session mode
            log.info("Running in SINGLE SESSION mode, date: {}", singleSessionDate);
            try {
                allEvents = sessionManager.loadSingleSession(singleSessionDate);
                horizonStart = java.time.LocalDate.parse(singleSessionDate,
                        java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                        .atStartOfDay(java.time.ZoneOffset.UTC)
                        .plusHours(10)
                        .toInstant();
            } catch (Exception e) {
                log.error("Failed to load single session {}: {}", singleSessionDate, e.getMessage(), e);
                kafkaPublisher.close();
                System.exit(1);
                return;
            }
        } else {
            // Horizon-based mode (default)
            try {
                horizonStart = Instant.parse(config.getReplay().getHorizonStart());
                log.info("Running in HORIZON mode, start: {}", horizonStart);
                allEvents = sessionManager.loadEventsFromHorizon(horizonStart);
            } catch (Exception e) {
                log.error("Invalid horizon_start in config: {}", config.getReplay().getHorizonStart());
                kafkaPublisher.close();
                System.exit(1);
                return;
            }
        }

        log.info("Loaded {} total trade events", allEvents.size());

        if (allEvents.isEmpty()) {
            log.warn("No events to replay. Exiting.");
            kafkaPublisher.close();
            System.exit(0);
            return;
        }

        // Create deterministic scheduler
        DeterministicScheduler scheduler = new DeterministicScheduler(
                config.getScheduler(),
                allEvents
        );

        // Runtime shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered, closing Kafka publisher...");
            kafkaPublisher.close();
            log.info("Shutdown complete.");
        }));

        // Run scheduler - publishes events to Kafka
        log.info("Starting replay loop");
        try {
            scheduler.run(horizonStart, tradeEvent -> {
                // publish all ticks to a single topic equal to the configured topic prefix
                String topic = config.getKafka().getTopicPrefix();
                kafkaPublisher.send(topic, tradeEvent.getSymbolCode(), tradeEvent);
            });
        } catch (Exception e) {
            log.error("Scheduler failed: {}", e.getMessage(), e);
            kafkaPublisher.close();
            System.exit(1);
        }

        // Send one sentinel tick per symbol far into the future so Kafka Streams
        // event-time advances past all window grace periods and flushes suppressed candles.
        log.info("Sending flush sentinels to advance stream time...");
        Instant flushTime = allEvents.stream()
                .map(TradeEvent::getEventTimeOriginal)
                .filter(t -> t != null)
                .max(java.util.Comparator.naturalOrder())
                .orElse(Instant.now())
                .plus(java.time.Duration.ofHours(1));

        allEvents.stream()
                .map(TradeEvent::getSymbolCode)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .forEach(symbol -> {
                    TradeEvent sentinel = new TradeEvent.Builder()
                            .symbolCode(symbol)
                            .eventTimeOriginal(flushTime)
                            .tradePrice(0.0)
                            .volumeTraded(0L)
                            .build();
                    kafkaPublisher.send(config.getKafka().getTopicPrefix(), symbol, sentinel);
                });

        // Flush and close
        log.info("Replay complete, flushing Kafka producer...");
        kafkaPublisher.close();
        log.info("Publisher service shut down gracefully.");
    }
}
