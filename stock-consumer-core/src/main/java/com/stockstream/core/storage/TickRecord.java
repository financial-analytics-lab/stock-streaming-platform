package com.stockstream.core.storage;

import com.stockstream.core.model.Tick;

import java.nio.ByteBuffer;

public class TickRecord {
    static final int SIZE_BYTES = 52;

    public static byte[] serialize(Tick tick) {
        ByteBuffer buf = ByteBuffer.allocate(SIZE_BYTES);
        buf.putLong(tick.tickId());
        buf.putLong(tick.timestamp().toEpochMilli());
        buf.putLong(tick.publishedAt().toEpochMilli());
        buf.putLong(tick.price().unscaledValue().longValueExact());
        buf.putInt(tick.price().scale());
        buf.putLong(tick.volume());
        buf.putLong(tick.lagMs());
        return buf.array();
    }



}
