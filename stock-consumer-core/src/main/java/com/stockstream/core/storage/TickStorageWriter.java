package com.stockstream.core.storage;

import com.stockstream.core.model.Tick;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TickStorageWriter implements AutoCloseable {
    private static final Logger log = Logger.getLogger(TickStorageWriter.class.getName());

    private final Path baseDir;
    private final int batchSize;

    private final ConcurrentHashMap<String, List<Tick>> buffers = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "tick-storage-flusher");
                t.setDaemon(true);
                return t;
            });

    /**
     * @param baseDir       root directory for tick files (created if absent)
     * @param batchSize     number of ticks to accumulate before a size-triggered flush
     * @param flushInterval how often the periodic flush runs (regardless of buffer size)
     * @param flushUnit     time unit for flushInterval
     */
    public TickStorageWriter(Path baseDir, int batchSize,
                             long flushInterval, TimeUnit flushUnit) throws IOException {
        if (batchSize < 1) throw new IllegalArgumentException("batchSize must be >= 1");
        this.baseDir   = baseDir;
        this.batchSize = batchSize;
        Files.createDirectories(baseDir);

        scheduler.scheduleAtFixedRate(this::flushAll,
                flushInterval, flushInterval, flushUnit);
        log.info("TickStorageWriter started. baseDir=" + baseDir
                + " batchSize=" + batchSize
                + " flushInterval=" + flushInterval + " " + flushUnit);
    }


    /**
     * Accept a tick. Returns immediately; disk I/O happens on the flusher thread
     * or inline when the buffer for this symbol is full.
     */
    public void add(Tick tick) {
        String symbol = tick.symbol().toUpperCase();
        buffers.compute(symbol, (k, buf) -> {
            if (buf == null) buf = new ArrayList<>(batchSize);
            buf.add(tick);
            return buf;
        });

        // Size-triggered flush — swap the buffer out and write on the calling thread.
        // This keeps the flusher thread free and spreads I/O across consumer threads.
        List<Tick> toFlush = null;
        synchronized (buffers) {
            List<Tick> current = buffers.get(symbol);
            if (current != null && current.size() >= batchSize) {
                toFlush = buffers.put(symbol, new ArrayList<>(batchSize));
            }
        }
        if (toFlush != null) {
            flush(symbol, toFlush);
        }
    }

    /** Force flush all buffers immediately.*/
    public void flushAll() {
        // Snapshot the keys to avoid holding the map lock during I/O
        for (String symbol : new ArrayList<>(buffers.keySet())) {
            List<Tick> toFlush;
            synchronized (buffers) {
                toFlush = buffers.put(symbol, new ArrayList<>(batchSize));
            }
            if (toFlush != null && !toFlush.isEmpty()) {
                flush(symbol, toFlush);
            }
        }
    }

    private void flush(String symbol, List<Tick> ticks) {
        if (ticks.isEmpty()) return;

        // Group by date — a batch may span midnight
        // (rare but correct to handle)
        var byDate = new java.util.TreeMap<LocalDate, List<Tick>>();
        for (Tick t : ticks) {
            LocalDate date = t.publishedAt().atZone(ZoneOffset.UTC).toLocalDate();
            byDate.computeIfAbsent(date, d -> new ArrayList<>()).add(t);
        }

        for (var entry : byDate.entrySet()) {
            Path file = resolveFile(symbol, entry.getKey());
            writeBatch(file, entry.getValue(), symbol);
        }
    }

    private void writeBatch(Path file, List<Tick> ticks, String symbol) {
        try {
            Files.createDirectories(file.getParent());

            // Allocate one ByteBuffer for the whole batch — single syscall
            ByteBuffer batch = ByteBuffer.allocate(ticks.size() * TickRecord.SIZE_BYTES);
            for (Tick tick : ticks) {
                batch.put(TickRecord.serialize(tick));
            }
            batch.flip();

            try (FileChannel ch = FileChannel.open(file,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.WRITE)) {
                while (batch.hasRemaining()) {
                    ch.write(batch);
                }
            }

            log.fine("Flushed " + ticks.size() + " ticks for " + symbol + " → " + file);

        } catch (IOException e) {
            log.log(Level.SEVERE,
                    "Failed to flush " + ticks.size() + " ticks for " + symbol + " to " + file, e);
        }
    }

    private Path resolveFile(String symbol, LocalDate date) {
        return baseDir
                .resolve(symbol)
                .resolve(date + ".bin");
    }

    @Override
    public void close() {
        scheduler.shutdown();
        flushAll();
        log.info("TickStorageWriter closed.");
    }

}
