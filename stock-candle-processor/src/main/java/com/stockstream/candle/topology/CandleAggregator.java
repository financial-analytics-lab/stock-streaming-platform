package com.stockstream.candle.topology;

import com.stockstream.candle.model.CandleInterval;
import com.stockstream.core.model.Candle;
import com.stockstream.core.model.Tick;
import org.apache.kafka.streams.kstream.Aggregator;
import org.apache.kafka.streams.kstream.Initializer;

import java.time.Instant;

public final class CandleAggregator {

    private CandleAggregator() {}

    public static Initializer<Candle> initializer(CandleInterval interval) {
        return () -> new Candle(
                null, interval.label,
                null, null,
                null, null, null, null,
                0L, 0L, null, null  // status injected by topology
        );
    }

    public static Aggregator<String, Tick, Candle> aggregator(CandleInterval interval) {
        return (symbol, tick, acc) -> {
            boolean first = acc.open() == null;
            return new Candle(
                    symbol,
                    interval.label,
                    null,   // windowStart injected by topology
                    null,   // windowEnd injected by topology
                    first ? tick.price() : acc.open(),
                    first ? tick.price() : acc.high().max(tick.price()),
                    first ? tick.price() : acc.low().min(tick.price()),
                    tick.price(),   // close = last seen price
                    acc.volume() + tick.volume(),
                    acc.tickCount() + 1,
                    Instant.now(),
                    null    // status injected by topology
            );
        };
    }
}
