package com.stockstream.candle;

import com.stockstream.candle.config.CandleConfigLoader;
import com.stockstream.candle.config.CandleProcessorConfig;
import com.stockstream.candle.topology.CandleTopologyBuilder;
import com.stockstream.core.logging.LoggerFactory;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;

import java.util.logging.Logger;

public final class CandleProcessorApp {

    private static final Logger LOG = LoggerFactory.getLogger(CandleProcessorApp.class);

    public static void main(String[] args) {
        CandleProcessorConfig config = CandleConfigLoader.load();

        Topology topology = new CandleTopologyBuilder(config.inputTopic()).build().build();

        KafkaStreams streams = new KafkaStreams(topology, config.toStreamsProperties());

        streams.setUncaughtExceptionHandler(exception -> {
            LOG.severe("Uncaught stream thread exception: " + exception.getMessage());
            return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD;
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutdown signal received — closing streams");
            streams.close();
        }, "candle-streams-shutdown"));

        streams.start();
        LOG.info("Candle processor started. Input topic: " + config.inputTopic()
                + ", threads: " + config.numStreamThreads());
    }
}
