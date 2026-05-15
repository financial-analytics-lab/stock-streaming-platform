package egx.publisher.metrics;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import egx.publisher.model.TradeEvent;

/**
 * Thread-safe, lock-free metrics collector for benchmarking the publisher service.
 * <p>
 * Captures per-event timing data for two benchmark metrics:
 * <ul>
 *   <li>{@code replay_lag_i}  = publish_time_actual_i − replay_due_time_i   (Equation 24)</li>
 *   <li>{@code transport_lag_i} = kafka_ack_time_i − publish_time_actual_i (Equation 25)</li>
 * </ul>
 * <p>
 * Events are buffered in a lock-free queue and periodically flushed to a CSV file
 * so that Kafka I/O does not block the main dispatch thread.
 */
public class PublisherMetrics implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(PublisherMetrics.class);

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    .withZone(ZoneOffset.UTC);

    private final Path outputPath;
    private final PrintWriter csvWriter;
    private final BlockingQueue<MetricRecord> buffer;
    private final Thread flushThread;
    private final AtomicLong eventsReceived;
    private final AtomicLong eventsWritten;

    private volatile boolean running = true;

    // ─── Per-event metric record ───────────────────────────────────────────────

    /**
     * One record per dispatched event.  All timestamps are epoch milliseconds
     * so that the CSV is easy to post-process in Python / pandas / Excel.
     */
    public static final class MetricRecord {
        public final Instant eventTimeOriginal;
        public final Instant replayDueTime;
        public final Instant publishTimeActual;
        public final long kafkaAckTime;           // epoch ms; 0 = not yet acked
        public final int kafkaPartition;          // -1 = not yet assigned
        public final long kafkaOffset;            // -1 = not yet assigned
        public final long replayLagMs;            // Equation 24
        public final long transportLagMs;         // Equation 25 (0 until acked)
        public final String symbolCode;
        public final Long sequenceId;

        public MetricRecord(TradeEvent event,
                            Instant publishTimeActual,
                            long kafkaAckTime,
                            int kafkaPartition,
                            long kafkaOffset,
                            long replayLagMs) {
            this.eventTimeOriginal = event.getEventTimeOriginal();
            this.replayDueTime    = event.getReplayDueTime();
            this.publishTimeActual = publishTimeActual;
            this.kafkaAckTime     = kafkaAckTime;
            this.kafkaPartition   = kafkaPartition;
            this.kafkaOffset      = kafkaOffset;
            this.replayLagMs      = replayLagMs;
            this.transportLagMs   = kafkaAckTime > 0
                    ? kafkaAckTime - publishTimeActual.toEpochMilli()
                    : 0;
            this.symbolCode  = event.getSymbolCode();
            this.sequenceId  = event.getSequenceId();
        }
    }

    // ─── Constructor ───────────────────────────────────────────────────────────

    /**
     * Creates a metrics collector that appends to a timestamped CSV file in the
     * given output directory.
     *
     * @param outputDir directory where {@code publisher_metrics.csv} will be created
     */
    public PublisherMetrics(String outputDir) throws IOException {
        Files.createDirectories(Paths.get(outputDir));
        String timestamp = LocalDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        this.outputPath = Paths.get(outputDir, "publisher_metrics_" + timestamp + ".csv");

        this.buffer = new LinkedBlockingQueue<>(100_000);
        this.eventsReceived = new AtomicLong(0);
        this.eventsWritten  = new AtomicLong(0);

        // CSV header
        this.csvWriter = new PrintWriter(
                new BufferedWriter(new FileWriter(outputPath.toFile(), true)));
        writeHeader();

        // Background flush thread
        this.flushThread = new Thread(this::flushLoop,
                "PublisherMetrics-Flush");
        this.flushThread.setDaemon(true);
        this.flushThread.start();

        log.info("PublisherMetrics initialised, output: {}", outputPath);
    }

    // ─── Public API ────────────────────────────────────────────────────────────

    /**
     * Called by the main dispatch thread immediately before sending to Kafka.
     * Records the wall-clock publish timestamp used for both lag equations.
     *
     * @return the captured {@code publish_time_actual} (caller keeps a copy for callback)
     */
    public Instant recordPublishTime() {
        return Instant.now();
    }

    /**
     * Called from the Kafka broker-ack callback to finalise transport_lag.
     * This method is invoked from the Kafka producer I/O thread — it is
     * signal-safe to call {@link BlockingQueue#offer(Object)} from any thread.
     *
     * @param publishTimeActual the timestamp captured by {@link #recordPublishTime()}
     * @param partition         Kafka partition the record was appended to
     * @param offset            Kafka offset assigned to the record
     * @param kafkaAckTime      epoch-ms broker log-append timestamp from {@code RecordMetadata}
     */
    public void recordAck(TradeEvent event,
                           Instant publishTimeActual,
                           int partition,
                           long offset,
                           long kafkaAckTime,
                           long replayLagMs) {
        MetricRecord record = new MetricRecord(
                event, publishTimeActual, kafkaAckTime, partition, offset, replayLagMs);
        buffer.offer(record);          // non-blocking; overflow is dropped with a warn
        eventsReceived.incrementAndGet();
    }

    /**
     * Closes the metrics collector: flushes the remaining buffer to disk
     * and writes a summary row.
     */
    @Override
    public void close() {
        running = false;
        try {
            flushThread.join(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        flushBuffer(Integer.MAX_VALUE);
        writeSummary();
        csvWriter.flush();
        csvWriter.close();
        log.info("PublisherMetrics closed. Written {} records to {}",
                eventsWritten.get(), outputPath);
    }

    // ─── Private helpers ────────────────────────────────────────────────────────

    private void writeHeader() {
        csvWriter.println(
                "event_time_original,"
                + "replay_due_time,"
                + "publish_time_actual,"
                + "kafka_ack_time,"
                + "kafka_partition,"
                + "kafka_offset,"
                + "replay_lag_ms,"    // Equation 24: publish_time_actual - replay_due_time
                + "transport_lag_ms," // Equation 25: kafka_ack_time - publish_time_actual
                + "symbol_code,"
                + "sequence_id"
        );
    }

    private void flushLoop() {
        while (running || !buffer.isEmpty()) {
            try {
                MetricRecord record = buffer.poll(500, TimeUnit.MILLISECONDS);
                if (record != null) {
                    writeRecord(record);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // Drain whatever is left
        flushBuffer(Integer.MAX_VALUE);
    }

    private void flushBuffer(int maxRecords) {
        int written = 0;
        while (written < maxRecords) {
            MetricRecord record = buffer.poll();
            if (record == null) break;
            writeRecord(record);
            written++;
        }
    }

    private synchronized void writeRecord(MetricRecord r) {
        csvWriter.println(String.join(",",
                ts(r.eventTimeOriginal),
                ts(r.replayDueTime),
                ts(r.publishTimeActual),
                String.valueOf(r.kafkaAckTime),
                String.valueOf(r.kafkaPartition),
                String.valueOf(r.kafkaOffset),
                String.valueOf(r.replayLagMs),
                String.valueOf(r.transportLagMs),
                r.symbolCode,
                String.valueOf(r.sequenceId)
        ));
        eventsWritten.incrementAndGet();
    }

    private static String ts(Instant instant) {
        return instant == null ? "" : TS_FMT.format(instant);
    }

    private void writeSummary() {
        // Called on close — dump aggregate stats as CSV comments
        csvWriter.println();
        csvWriter.println("# ── Summary ──────────────────────────────────────────────────────────────");
        csvWriter.println("# replay_lag  = publish_time_actual_i − replay_due_time_i   (Equation 24)");
        csvWriter.println("# transport_lag = kafka_ack_time_i − publish_time_actual_i (Equation 25)");
        csvWriter.println("#");
        csvWriter.println("# Total events received : " + eventsReceived.get());
        csvWriter.println("# Total events written  : " + eventsWritten.get());
    }
}
