package com.stockstream.candle.model;

import org.apache.kafka.streams.kstream.TimeWindows;

import java.time.Duration;

public enum CandleInterval {
    ONE_SECOND    ("1s",  Duration.ofSeconds(1),   Duration.ofSeconds(2),  "candles-1s"),
    FIVE_SECONDS  ("5s",  Duration.ofSeconds(5),   Duration.ofSeconds(5),  "candles-5s"),
    ONE_MINUTE    ("1m",  Duration.ofMinutes(1),   Duration.ofSeconds(10), "candles-1m"),
    FIVE_MINUTES  ("5m",  Duration.ofMinutes(5),   Duration.ofSeconds(30), "candles-5m"),
    FIFTEEN_MINUTES("15m", Duration.ofMinutes(15), Duration.ofMinutes(1),  "candles-15m");

    public final String label;
    public final Duration size;
    public final Duration grace;
    public final String outputTopic;

    CandleInterval(String label, Duration size, Duration grace, String outputTopic) {
        this.label = label;
        this.size = size;
        this.grace = grace;
        this.outputTopic = outputTopic;
    }

    public TimeWindows timeWindows() {
        return TimeWindows.ofSizeAndGrace(size, grace);
    }

    public String stateStoreName() {
        return "candle-store-" + label;
    }
}
