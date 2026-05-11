package egx.publisher.kafka;

import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import egx.publisher.config.AppConfig;
import egx.publisher.model.Tick;
import egx.publisher.model.TradeEvent;

/**
 * Kafka producer wrapper for publishing trade events to symbol-specific topics.
 * Uses asynchronous sending for high throughput.
 */
public class KafkaPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaPublisher.class);

    private final KafkaProducer<String, String> producer;
    private final String topicPrefix;
    private final ObjectMapper objectMapper;
    private final PartitionRouter partitionRouter;
    private final AtomicLong sentCount;
    private final AtomicLong errorCount;

    public KafkaPublisher(AppConfig.KafkaConfig config) {
        this.topicPrefix = config.getTopicPrefix();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.partitionRouter = new PartitionRouter();
        this.sentCount = new AtomicLong(0);
        this.errorCount = new AtomicLong(0);

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.LINGER_MS_CONFIG, config.getLingerMs());
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, config.getBatchSize());
        props.put(ProducerConfig.ACKS_CONFIG, config.getAcks());
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, config.getMaxInFlight());
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false);

        this.producer = new KafkaProducer<>(props);
        log.info("KafkaPublisher initialized with bootstrap servers: {}", config.getBootstrapServers());
    }

    /**
     * Sends a trade event to the appropriate Kafka topic asynchronously.
     *
     * @param topic   the topic name (e.g., "egx30-EGS30901C010")
     * @param key     the message key (typically symbol code for partition affinity)
     * @param event   the trade event to send
     * @return a Future that will complete when the record has been sent
     */
    public Future<?> send(String topic, String key, TradeEvent event) {
        try {
            // attach published_at and emit the public Tick shape
            Tick tick = event.toTick(Instant.now());
            String jsonValue = objectMapper.writeValueAsString(tick);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, jsonValue);

            Future<?> future = producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    log.error("Failed to send record to topic {}: {}", topic, exception.getMessage());
                    errorCount.incrementAndGet();
                } else {
                    long count = sentCount.incrementAndGet();
                    if (count % 100000 == 0) {
                        log.info("Sent {} records to Kafka", count);
                    }
                    log.trace("Sent record to topic {} partition {} offset {}",
                            metadata.topic(), metadata.partition(), metadata.offset());
                }
            });

            return future;
        } catch (Exception e) {
            log.error("Failed to serialize trade event: {}", e.getMessage());
            errorCount.incrementAndGet();
            throw new RuntimeException("Failed to send to Kafka", e);
        }
    }

    /**
     * Sends a trade event to a topic derived from the symbol code.
     *
     * @param event the trade event to send
     */
    public void send(TradeEvent event) {
        String topic = topicPrefix;
        send(topic, event.getSymbolCode(), event);
    }

    /**
     * Flushes any pending records and waits for completion.
     */
    public void flush() {
        log.info("Flushing Kafka producer...");
        producer.flush();
        log.info("Flush complete. Total sent: {}, Errors: {}", sentCount.get(), errorCount.get());
    }

    /**
     * Closes the producer gracefully, flushing pending records first.
     */
    public void close() {
        flush();
        log.info("Closing Kafka producer...");
        producer.close(Duration.ofSeconds(30));
        log.info("Kafka publisher closed. Final stats - Sent: {}, Errors: {}", sentCount.get(), errorCount.get());
    }

    /**
     * Returns the total number of successfully sent records.
     */
    public long getSentCount() {
        return sentCount.get();
    }

    /**
     * Returns the total number of errors encountered.
     */
    public long getErrorCount() {
        return errorCount.get();
    }
}