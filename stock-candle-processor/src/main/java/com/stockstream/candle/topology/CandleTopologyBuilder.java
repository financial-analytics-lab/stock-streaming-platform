package com.stockstream.candle.topology;

import com.stockstream.candle.model.Candle;
import com.stockstream.candle.model.CandleInterval;
import com.stockstream.candle.serializer.SerializerFactory;
import com.stockstream.core.model.Tick;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.state.WindowStore;

import java.time.Instant;
import java.util.logging.Logger;

public final class CandleTopologyBuilder {

    private static final Logger LOG = java.util.logging.Logger.getLogger(CandleTopologyBuilder.class.getName());

    private final String inputTopic;

    public CandleTopologyBuilder(String inputTopic) {
        this.inputTopic = inputTopic;
    }

    public StreamsBuilder build() {
        StreamsBuilder builder = new StreamsBuilder();

        KStream<String, Tick> ticks = builder.stream(
                inputTopic,
                Consumed.with(Serdes.String(), SerializerFactory.tickSerializer())
                        .withTimestampExtractor(new TickTimestampExtractor())
        );

        ticks.peek((key, tick) -> LOG.info("[TICK] symbol=" + key + " price=" + (tick == null ? "NULL_TICK" : tick.price()) + " ts=" + (tick == null ? "?" : tick.timestamp())));

        for (CandleInterval interval : CandleInterval.values()) {
            addBranch(ticks, interval);
        }

        return builder;
    }

    private void addBranch(KStream<String, Tick> ticks, CandleInterval interval) {
        ticks
                // key is already the symbol — groupByKey avoids a repartition topic
                .groupByKey(Grouped.with(Serdes.String(), SerializerFactory.tickSerializer()))
                .windowedBy(interval.timeWindows())
                .aggregate(
                        CandleAggregator.initializer(interval),
                        CandleAggregator.aggregator(interval),
                        Materialized.<String, Candle, WindowStore<Bytes, byte[]>>as(interval.stateStoreName())
                                .withKeySerde(Serdes.String())
                                .withValueSerde(SerializerFactory.candleSerializer())
                )
                // Emit exactly one record per (symbol, window) after window + grace has elapsed
                .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()))
                .toStream()
                .peek((wk, candle) -> LOG.info("[CANDLE-" + interval.label + "] symbol=" + wk.key() + " open=" + candle.open() + " close=" + candle.close() + " volume=" + candle.volume() + " ticks=" + candle.tickCount() + " window=[" + Instant.ofEpochMilli(wk.window().start()) + "," + Instant.ofEpochMilli(wk.window().end()) + ")"));
                // windowedKey carries the window bounds — inject them into the Candle here
                .map((windowedKey, candle) -> {
                    Candle completed = new Candle(
                            windowedKey.key(),
                            candle.interval(),
                            Instant.ofEpochMilli(windowedKey.window().start()),
                            Instant.ofEpochMilli(windowedKey.window().end()),
                            candle.open(),
                            candle.high(),
                            candle.low(),
                            candle.close(),
                            candle.volume(),
                            candle.tickCount(),
                            Instant.now()
                    );
                    return KeyValue.pair(windowedKey.key(), completed);
                })
                .to(interval.outputTopic, Produced.with(Serdes.String(), SerializerFactory.candleSerializer()));
    }
}
