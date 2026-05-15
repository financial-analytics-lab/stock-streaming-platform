package com.stockstream.candle.topology;

import com.stockstream.core.model.Tick;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.processor.TimestampExtractor;

public final class TickTimestampExtractor implements TimestampExtractor {

    @Override
    public long extract(ConsumerRecord<Object, Object> record, long partitionTime) {
        if (record.value() instanceof Tick tick) {
            return tick.timestamp().toEpochMilli();
        }
        // Fall back to stream (partition) time if value is not a Tick
        return partitionTime;
    }
}
