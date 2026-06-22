package com.stockstream.dashboard.store;

import com.stockstream.core.model.Tick;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class TickStorageReader {
    private static final int RECORD_SIZE = 128;
    private static final int PUBLISHED_AT_OFFSET = 16; // Offset within the 128-byte record
    private final Path baseDir = Paths.get("DATA");

    private final DateTimeFormatter dateDirFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private final DateTimeFormatter hourFormatter = DateTimeFormatter.ofPattern("hh:00 a", Locale.US);
    private final DateTimeFormatter minFormatter = DateTimeFormatter.ofPattern("mm");

    /**
     * Executes a lightning-fast range query using binary search directly over the on-disk segment.
     */
    public List<Tick> getRangeFromDisk(String symbol, Instant startRange, Instant endRange) {
        List<Tick> result = new ArrayList<>();

        // Find the relevant 15-minute file segment path
        // (Note: If queries cross 15-minute boundaries, iterate through all overlapping intervals)
        Path targetFile = resolvePartitionPath(symbol, startRange);

        if (!Files.exists(targetFile)) {
            return result; // No data recorded on disk for this specific segment window
        }

        try (FileChannel channel = FileChannel.open(targetFile, StandardOpenOption.READ)) {
            long totalRecords = channel.size() / RECORD_SIZE;
            if (totalRecords == 0) return result;

            // 1. Binary Search on disk to find the index of the first record >= startRange
            long targetIndex = binarySearchFirstRecord(channel, totalRecords, startRange.toEpochMilli());

            if (targetIndex == -1) {
                return result; // All elements in this file are older than the start range
            }

            // 2. Sequential Scan from the matched index forward
            channel.position(targetIndex * RECORD_SIZE);
            ByteBuffer recordBuffer = ByteBuffer.allocate(RECORD_SIZE);

            while (channel.read(recordBuffer) == RECORD_SIZE) {
                recordBuffer.flip();
                Tick tick = deserialize(recordBuffer);

                // Break out immediately if we step past our designated end-query boundary
                if (tick.publishedAt().isAfter(endRange)) {
                    break;
                }

                if (!tick.publishedAt().isBefore(startRange)) {
                    result.add(tick);
                }
                recordBuffer.clear();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Low-level on-disk binary search targeting the publishedAt timestamp field.
     * Returns the lowest record index meeting the threshold criteria.
     */
    private long binarySearchFirstRecord(FileChannel channel, long totalRecords, long targetEpochMs) throws IOException {
        long low = 0;
        long high = totalRecords - 1;
        long resultIndex = -1;

        ByteBuffer timeBuffer = ByteBuffer.allocate(8);

        while (low <= high) {
            long mid = (low + high) >>> 1;

            // Calculate position directly to the publishedAt field inside the mid record
            long targetBytePosition = (mid * RECORD_SIZE) + PUBLISHED_AT_OFFSET;
            channel.position(targetBytePosition);
            timeBuffer.clear();
            channel.read(timeBuffer);
            timeBuffer.flip();

            long midTime = timeBuffer.getLong();

            if (midTime >= targetEpochMs) {
                resultIndex = mid; // Candidate match found, keep searching left for the absolute earliest match
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return resultIndex;
    }

    /**
     * Reconstructs the immutable Tick record object directly from the raw byte stream buffer.
     */
    private Tick deserialize(ByteBuffer buffer) {
        long tickId = buffer.getLong();
        Instant timestamp = Instant.ofEpochMilli(buffer.getLong());
        Instant publishedAt = Instant.ofEpochMilli(buffer.getLong());

        // Reconstruct BigDecimal with scale 4 from the long value
        long scaledPrice = buffer.getLong();
        BigDecimal price = new BigDecimal(BigInteger.valueOf(scaledPrice), 4);

        long volume = buffer.getLong();
        long lagMs = buffer.getLong();

        byte[] symBytes = new byte[16];
        buffer.get(symBytes);
        String symbol = new String(symBytes, StandardCharsets.UTF_8).replace("\0", "").trim();

        byte[] secBytes = new byte[64];
        buffer.get(secBytes);
        String securityName = new String(secBytes, StandardCharsets.UTF_8).replace("\0", "").trim();

        return new Tick(tickId, securityName, symbol, price, volume, timestamp, publishedAt, lagMs);
    }

    private Path resolvePartitionPath(String symbol, Instant time) {
        ZonedDateTime zdt = time.atZone(ZoneId.of("Africa/Cairo"));
        String dateDir = zdt.format(dateDirFormatter);

        int minute = zdt.getMinute();
        int startRangeMin = (minute / 15) * 15;

        ZonedDateTime windowStart = zdt.withMinute(startRangeMin).withSecond(0).withNano(0);
        ZonedDateTime windowEnd = windowStart.plusMinutes(15);

        String hourBlockDir = String.format("%s to %s",
                windowStart.format(hourFormatter),
                windowStart.withMinute(0).plusHours(1).format(hourFormatter));

        String segmentFile = String.format("%s to %s seg.bin",
                windowStart.format(minFormatter),
                windowEnd.format(minFormatter));

        return baseDir
                .resolve(symbol.toUpperCase())
                .resolve(dateDir)
                .resolve(hourBlockDir)
                .resolve(segmentFile);
    }
}