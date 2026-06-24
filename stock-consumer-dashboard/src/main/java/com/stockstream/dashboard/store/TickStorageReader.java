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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
 
@Service
public class TickStorageReader {
    private static final int RECORD_SIZE = 128;
    private static final int PUBLISHED_AT_OFFSET = 16; // Offset within the 128-byte record
    private final Path baseDir;
 
    private final DateTimeFormatter dateDirFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final ZoneId ZONE_CAIRO = ZoneId.of("Africa/Cairo");
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH-mm");
 
    public TickStorageReader(@Value("${storage.base-dir:DATA}") String baseDir) {
        this.baseDir = Paths.get(baseDir);
    }
 
    public static class SegmentInterval {
        public final Instant start;
        public final Instant end;
        public final Path path;
 
        public SegmentInterval(Instant start, Instant end, Path path) {
            this.start = start;
            this.end = end;
            this.path = path;
        }
    }
 
    private SegmentInterval parseSegmentInterval(Path path) {
        try {
            String segmentFile = path.getFileName().toString(); // e.g., "23-40 to 23-41 seg.bin"
            String dateDir = path.getParent().getFileName().toString(); // e.g. "24-06-2026"
 
            // 1. Parse date
            java.time.LocalDate date = java.time.LocalDate.parse(dateDir, dateDirFormatter);
 
            // 2. Parse start and end times from segmentFile (format: "HH-mm to HH-mm seg.bin")
            String cleanName = segmentFile.replace(" seg.bin", ""); // "23-40 to 23-41"
            String[] timeParts = cleanName.split(" to ");
            
            String startStr = timeParts[0].trim(); // "23-40"
            String endStr = timeParts[1].trim(); // "23-41"
 
            String[] startHMS = startStr.split("-");
            int startHour = Integer.parseInt(startHMS[0]);
            int startMin = Integer.parseInt(startHMS[1]);
 
            String[] endHMS = endStr.split("-");
            int endHour = Integer.parseInt(endHMS[0]);
            int endMin = Integer.parseInt(endHMS[1]);
 
            ZonedDateTime startZdt = ZonedDateTime.of(date, java.time.LocalTime.of(startHour, startMin), ZONE_CAIRO);
            
            // Handle wrap around midnight if end time is numerically less than start time
            ZonedDateTime endZdt;
            if (endHour < startHour || (endHour == startHour && endMin < startMin)) {
                endZdt = ZonedDateTime.of(date.plusDays(1), java.time.LocalTime.of(endHour, endMin), ZONE_CAIRO);
            } else {
                endZdt = ZonedDateTime.of(date, java.time.LocalTime.of(endHour, endMin), ZONE_CAIRO);
            }
 
            return new SegmentInterval(startZdt.toInstant(), endZdt.toInstant(), path);
        } catch (Exception e) {
            return null; // Not a valid segment file structure
        }
    }
 
    /**
     * Executes a lightning-fast range query using binary search directly over the on-disk segments.
     */
    public List<Tick> getRangeFromDisk(String symbol, Instant startRange, Instant endRange) {
        List<Tick> result = new ArrayList<>();
 
        // 1. Locate all overlapping segment files across the query range
        List<SegmentInterval> intervals = new ArrayList<>();
        ZonedDateTime startZdt = startRange.atZone(ZONE_CAIRO);
        ZonedDateTime endZdt = endRange.atZone(ZONE_CAIRO);
 
        java.time.LocalDate startDate = startZdt.toLocalDate();
        java.time.LocalDate endDate = endZdt.toLocalDate();
 
        for (java.time.LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            String dateStr = date.format(dateDirFormatter);
            Path datePath = baseDir.resolve(symbol.toUpperCase()).resolve(dateStr);
            if (Files.exists(datePath)) {
                try (java.util.stream.Stream<Path> walk = Files.walk(datePath)) {
                    walk.filter(Files::isRegularFile)
                        .forEach(p -> {
                            SegmentInterval interval = parseSegmentInterval(p);
                            if (interval != null) {
                                // Check if interval overlaps with the range [startRange, endRange]
                                if (!interval.start.isAfter(endRange) && !interval.end.isBefore(startRange)) {
                                    intervals.add(interval);
                                }
                            }
                        });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
 
        // Sort intervals chronologically
        intervals.sort(java.util.Comparator.comparing(i -> i.start));
 
        // 2. Read from each overlapping segment file
        for (SegmentInterval interval : intervals) {
            Path targetFile = interval.path;
            try (FileChannel channel = FileChannel.open(targetFile, StandardOpenOption.READ)) {
                long totalRecords = channel.size() / RECORD_SIZE;
                if (totalRecords == 0) continue;
 
                // Binary Search on disk to find the index of the first record >= startRange
                long targetIndex = binarySearchFirstRecord(channel, totalRecords, startRange.toEpochMilli());
                if (targetIndex == -1) {
                    continue; // All elements in this file are older than the start range
                }
 
                // Sequential Scan from the matched index forward
                channel.position(targetIndex * RECORD_SIZE);
                ByteBuffer recordBuffer = ByteBuffer.allocate(RECORD_SIZE);
 
                while (channel.read(recordBuffer) == RECORD_SIZE) {
                    recordBuffer.flip();
                    Tick tick = deserialize(recordBuffer);
 
                    // Break out if we step past our designated end-query boundary
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
 
        ZonedDateTime windowStart = zdt.withSecond(0).withNano(0);
        ZonedDateTime windowEnd = windowStart.plusMinutes(1);
 
        String segmentFile = String.format("%s to %s seg.bin",
                windowStart.format(timeFormatter),
                windowEnd.format(timeFormatter));
 
        return baseDir
                .resolve(symbol.toUpperCase())
                .resolve(dateDir)
                .resolve(segmentFile);
    }
}