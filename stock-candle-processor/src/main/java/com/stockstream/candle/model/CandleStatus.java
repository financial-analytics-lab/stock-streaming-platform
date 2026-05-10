package com.stockstream.candle.model;

public enum CandleStatus {
    OPEN,   // forming — more ticks may arrive
    CLOSED  // final — window + grace elapsed
}
