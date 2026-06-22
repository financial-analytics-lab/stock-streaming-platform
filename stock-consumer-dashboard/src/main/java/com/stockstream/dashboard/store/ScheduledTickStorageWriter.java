package com.stockstream.dashboard.store;
 
import com.stockstream.core.model.Tick;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
 
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
 
@Service
public class ScheduledTickStorageWriter {
    private static final Logger log = Logger.getLogger(ScheduledTickStorageWriter.class.getName());
 
    // Root directory where the partitioned data will be written
    private final Path baseDir = Paths.get("DATA");
    private final TickStore tickStore;
 
    // Time formatters to match your exact directory naming specifications
    private final DateTimeFormatter dateDirFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private final DateTimeFormatter hourFormatter = DateTimeFormatter.ofPattern("hh:00 a", Locale.US);
    private final DateTimeFormatter minFormatter = DateTimeFormatter.ofPattern("mm");
 
    public ScheduledTickStorageWriter(TickStore tickStore) {
        this.tickStore = tickStore;
    }
 
    /**
     * Runs every 15 minutes on the clock (e.g., 10:00, 10:15, 10:30, 10:45...)
     * Aligned to the Africa/Cairo trading timezone.
     */
    @Scheduled(cron = "0 0/15 * * * MON-FRI", zone = "Africa/Cairo")
    public void flushPastFifteenMinuteWindow() throws IOException {
        // 1. Calculate the exact 15-minute market time window that just concluded
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Africa/Cairo"));
        ZonedDateTime windowEnd = now.withSecond(0).withNano(0);
        ZonedDateTime windowStart = windowEnd.minusMinutes(15);
 
        Instant from = windowStart.toInstant();
        Instant to = windowEnd.toInstant();
 
        log.info(String.format("Starting scheduled disk flush for window: %s to %s", windowStart, windowEnd));
 
        // 2. Iterate through all symbols currently active in our memory store
        for (String symbol : tickStore.getSymbols()) {
            List<Tick> ticksInWindow = tickStore.getRange(symbol, from, to);
 
            if (!ticksInWindow.isEmpty()) {
                Path targetFile = resolvePartitionPath(symbol, windowStart, windowEnd);
                writeBatchToDisk(targetFile, ticksInWindow);
            }
        }
    }
 
    /**
     * Resolves paths to match the exact nested directory structure requested:
     * DATA / [Symbol] / [dd-MM-yyyy] / [HH:MM AM to HH:MM AM] / [MM to MM seg].bin
     */
    private Path resolvePartitionPath(String symbol, ZonedDateTime start, ZonedDateTime end) {
        String dateDir = start.format(dateDirFormatter);
 
        // Generates: "10:00 AM to 11:00 AM" (Handles cross-hour bounds cleanly)
        String hourBlockDir = String.format("%s to %s",
                start.format(hourFormatter),
                start.plusHours(1).withMinute(0).format(hourFormatter));
 
        // Generates: "00 to 15 seg.bin"
        String segmentFile = String.format("%s to %s seg.bin",
                start.format(minFormatter),
                end.format(minFormatter));
 
        return baseDir
                .resolve(symbol.toUpperCase())
                .resolve(dateDir)
                .resolve(hourBlockDir)
                .resolve(segmentFile);
    }
 
    /**
     * Performs low-latency sequential binary write operations via NIO FileChannels
     */
    private void writeBatchToDisk(Path file, List<Tick> ticks) throws IOException {
        Files.createDirectories(file.getParent());
        int RECORD_SIZE = 128;
        ByteBuffer buffer = ByteBuffer.allocate(ticks.size() * RECORD_SIZE);
 
        for (Tick tick : ticks) {
            buffer.putLong(tick.tickId());
            buffer.putLong(tick.timestamp().toEpochMilli());
            buffer.putLong(tick.publishedAt().toEpochMilli());
 
            // Scale BigDecimal safely to a 64-bit long (up to 4 decimal places)
            long scaledPrice = tick.price().scaleByPowerOfTen(4).longValue();
            buffer.putLong(scaledPrice);
 
            buffer.putLong(tick.volume());
            buffer.putLong(tick.lagMs());
 
            // Serialize and pad 'symbol' to 16 bytes
            byte[] symBytes = new byte[16];
            byte[] rawSym = tick.symbol().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            System.arraycopy(rawSym, 0, symBytes, 0, Math.min(rawSym.length, 16));
            buffer.put(symBytes);
 
            // Serialize and pad 'securityName' to 64 bytes
            byte[] secBytes = new byte[64];
            byte[] rawSec = tick.securityName().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            System.arraycopy(rawSec, 0, secBytes, 0, Math.min(rawSec.length, 64));
            buffer.put(secBytes);
        }
        buffer.flip();
 
        try (FileChannel ch = FileChannel.open(file,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
                StandardOpenOption.WRITE)) {
            while (buffer.hasRemaining()) {
                ch.write(buffer);
            }
        }
        log.fine("Successfully wrote " + ticks.size() + " ticks to disk segment: " + file);
    }
}
