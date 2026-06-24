package com.stockstream.dashboard.service;

import com.stockstream.core.model.Tick;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class CsvMetricsWriter {

    private static final Logger log = Logger.getLogger(CsvMetricsWriter.class.getName());
    private BufferedWriter writer;

    public CsvMetricsWriter() {
        try {
            Path filePath = Paths.get("tick_metrics_2.csv");
            boolean isNewFile = !filePath.toFile().exists();

            writer = new BufferedWriter(new FileWriter(filePath.toFile(), true));
            if (isNewFile) {
                writer.write("system_time_ms,published_at_ms,record_latency_ms,symbol,price,volume\n");
                writer.flush();
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Failed to initialize CSV writer", e);
        }
    }

    public synchronized void writeTickMetrics(Tick tick, long systemTime) {
        if (writer == null) return;

        try {
            long tickTimestampMs = tick.publishedAt() != null ? tick.publishedAt().toEpochMilli() : 0;
            long latency = systemTime - tickTimestampMs;
            String row = String.format("%d,%d,%d,\"%s\",%f,%d\n",
                    systemTime,
                    tickTimestampMs,
                    latency,
                    tick.symbol(),
                    tick.price(),
                    tick.volume());
            writer.write(row);
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to write row to CSV", e);
        }
    }

    @PreDestroy
    public void close() {
        if (writer != null) {
            try {
                writer.flush();
                writer.close();
            } catch (IOException e) {
                log.log(Level.WARNING, "Failed to close CSV writer", e);
            }
        }
    }
}

