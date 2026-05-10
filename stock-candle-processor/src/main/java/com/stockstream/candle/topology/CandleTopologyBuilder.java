package com.stockstream.candle.topology;

import java.time.Instant;
import java.util.logging.Logger;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.state.WindowStore;

import com.stockstream.candle.model.Candle;
import com.stockstream.candle.model.CandleInterval;
import com.stockstream.candle.model.CandleStatus;
import com.stockstream.candle.serializer.SerializerFactory;
import com.stockstream.core.model.Tick;

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

 /*        ticks.peek((key, tick) -> LOG.info("[TICK] symbol=" + key
                + " price=" + (tick == null ? "NULL" : tick.price())
                + " ts=" + (tick == null ? "?" : tick.timestamp())));
 */
        for (CandleInterval interval : CandleInterval.values()) {
            addBranch(ticks, interval);
        }

        return builder;
    }

    private void addBranch(KStream<String, Tick> ticks, CandleInterval interval) {

        // Single KTable — aggregation runs exactly once per tick
        KTable<Windowed<String>, Candle> table = ticks
                .groupByKey(Grouped.with(Serdes.String(), SerializerFactory.tickSerializer()))
                .windowedBy(interval.timeWindows())
                .aggregate(
                        CandleAggregator.initializer(interval),
                        CandleAggregator.aggregator(interval),
                        Materialized.<String, Candle, WindowStore<Bytes, byte[]>>as(interval.stateStoreName())
                                .withKeySerde(Serdes.String())
                                .withValueSerde(SerializerFactory.candleSerializer())
                );

        // ── LIVE: emit on every tick update (status=OPEN) ─────────────────────
        table.toStream()
                .map((wk, candle) -> KeyValue.pair(
                        compositeKey(wk),
                        buildCandle(wk, candle, CandleStatus.OPEN)
                ))
                .peek((key, c) -> LOG.info("[OPEN-" + interval.label + "] key=" + key
                        + " close=" + c.close() + " ticks=" + c.tickCount()))
                .to(interval.outputTopic, Produced.with(Serdes.String(), SerializerFactory.candleSerializer()));

        // ── CLOSED: suppress until window closes, emit once (status=CLOSED) ───
        table.suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()))
                .toStream()
                .map((wk, candle) -> KeyValue.pair(
                        compositeKey(wk),
                        buildCandle(wk, candle, CandleStatus.CLOSED)
                ))
                .peek((key, c) -> LOG.info("[CLOSED-" + interval.label + "] key=" + key
                        + " open=" + c.open() + " close=" + c.close()
                        + " volume=" + c.volume() + " ticks=" + c.tickCount()))
                .to(interval.outputTopic, Produced.with(Serdes.String(), SerializerFactory.candleSerializer()));
    }

    // key format: "SYMBOL:windowStartISO"  e.g. "EGS30901C010:2025-01-02T10:00:00Z"
    private static String compositeKey(Windowed<String> wk) {
        return wk.key() + ":" + Instant.ofEpochMilli(wk.window().start());
    }

    private static Candle buildCandle(Windowed<String> wk, Candle agg, CandleStatus status) {
        Instant wStart = Instant.ofEpochMilli(wk.window().start());
        Instant wEnd   = Instant.ofEpochMilli(wk.window().end());
        return new Candle(
                wk.key(),
                agg.interval(),
                wStart,
                wEnd,
                agg.open(),
                agg.high(),
                agg.low(),
                agg.close(),
                agg.volume(),
                agg.tickCount(),
                Instant.now(),
                status
        );
    }
}
