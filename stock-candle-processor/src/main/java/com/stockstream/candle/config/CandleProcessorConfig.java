package com.stockstream.candle.config;

import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;

import java.util.Properties;

public record CandleProcessorConfig(
        String bootstrapServers,
        String applicationId,
        String inputTopic,
        int numStreamThreads,
        String stateDir,
        String autoOffsetReset,
        long commitIntervalMs
) {
    static final String DEFAULT_APP_ID        = "candle-processor";
    static final String DEFAULT_INPUT         = "stock-ticks";
    static final String DEFAULT_STATE_DIR     = "/tmp/kafka-streams/candle-processor";
    static final int    DEFAULT_THREADS       = 2;
    static final long   DEFAULT_COMMIT_MS     = 100L;

    public CandleProcessorConfig {
        if (bootstrapServers == null || bootstrapServers.isBlank())
            throw new IllegalArgumentException("bootstrapServers is required");
        if (applicationId == null || applicationId.isBlank())
            applicationId = DEFAULT_APP_ID;
        if (inputTopic == null || inputTopic.isBlank())
            inputTopic = DEFAULT_INPUT;
        if (stateDir == null || stateDir.isBlank())
            stateDir = DEFAULT_STATE_DIR;
        if (numStreamThreads <= 0)
            numStreamThreads = DEFAULT_THREADS;
        if (autoOffsetReset == null || autoOffsetReset.isBlank())
            autoOffsetReset = "earliest";
        if (commitIntervalMs <= 0)
            commitIntervalMs = DEFAULT_COMMIT_MS;
    }

    public Properties toStreamsProperties() {
        Properties p = new Properties();
        p.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
        p.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        p.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, numStreamThreads);
        p.put(StreamsConfig.STATE_DIR_CONFIG, stateDir);
        p.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG,
                org.apache.kafka.common.serialization.Serdes.StringSerde.class.getName());
        p.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.AT_LEAST_ONCE);
        p.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                LogAndContinueExceptionHandler.class.getName());
        p.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        p.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, commitIntervalMs);
        return p;
    }
}
