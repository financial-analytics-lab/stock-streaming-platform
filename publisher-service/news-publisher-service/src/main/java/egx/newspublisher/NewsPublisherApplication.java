package egx.newspublisher;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import egx.newspublisher.config.AppConfig;
import egx.newspublisher.kafka.KafkaPublisher;
import egx.newspublisher.loader.NewsSessionManager;
import egx.newspublisher.model.NewsEvent;
import egx.newspublisher.scheduler.DeterministicScheduler;

public class NewsPublisherApplication {

    private static final Logger log = LoggerFactory.getLogger(NewsPublisherApplication.class);

    public static void main(String[] args) {
        log.info("Starting EGX News Publisher Service");

        String configPath = args.length > 0 ? args[0] : "config/application.yaml";

        AppConfig config;
        try {
            config = AppConfig.load(configPath);
            log.info("Configuration loaded successfully from {}", configPath);
        } catch (Exception e) {
            log.error("Failed to load configuration from {}: {}", configPath, e.getMessage(), e);
            System.exit(1);
            return;
        }

        KafkaPublisher kafkaPublisher;
        try {
            kafkaPublisher = new KafkaPublisher(config.getKafka());
        } catch (Exception e) {
            log.error("Failed to initialize Kafka publisher: {}", e.getMessage(), e);
            System.exit(1);
            return;
        }

        String newsPath = config.getNews() != null && config.getNews().getFilePath() != null
                ? config.getNews().getFilePath()
                : "../publisher-service/all_news_by_date.json";
        NewsSessionManager sessionManager = new NewsSessionManager(newsPath);

        List<NewsEvent> allEvents;
        Instant horizonStart;

        String singleDate = config.getReplay() != null ? config.getReplay().getSingleDate() : null;
        if (singleDate != null && !singleDate.isBlank()) {
            log.info("Running in SINGLE DATE mode, date: {}", singleDate);
            try {
                allEvents = sessionManager.loadSingleDate(singleDate);
                horizonStart = LocalDate.parse(singleDate)
                        .atStartOfDay(ZoneOffset.UTC)
                        .plusHours(10)
                        .toInstant();
            } catch (Exception e) {
                log.error("Failed to load single date {}: {}", singleDate, e.getMessage(), e);
                kafkaPublisher.close();
                System.exit(1);
                return;
            }
        } else {
            try {
                horizonStart = Instant.parse(config.getReplay().getHorizonStart());
                log.info("Running in HORIZON mode, start: {}", horizonStart);
                allEvents = sessionManager.loadEventsFromHorizon(horizonStart);
            } catch (Exception e) {
                log.error("Invalid horizon_start in config: {}", config.getReplay().getHorizonStart(), e);
                kafkaPublisher.close();
                System.exit(1);
                return;
            }
        }

        log.info("Loaded {} total news events", allEvents.size());

        if (allEvents.isEmpty()) {
            log.warn("No news events to replay. Exiting.");
            kafkaPublisher.close();
            System.exit(0);
            return;
        }

        DeterministicScheduler<NewsEvent> scheduler = new DeterministicScheduler<>(config.getScheduler(), allEvents);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered, closing Kafka publisher...");
            kafkaPublisher.close();
        }));

        log.info("Starting news replay loop");
        try {
            scheduler.run(horizonStart, event -> kafkaPublisher.send(config.getKafka().getTopicPrefix(), event.getPartitionKey(), event));
        } catch (Exception e) {
            log.error("Scheduler failed: {}", e.getMessage(), e);
            kafkaPublisher.close();
            System.exit(1);
        }

        kafkaPublisher.close();
        log.info("News publisher service shut down gracefully.");
    }
}
