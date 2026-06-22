package com.stockstream.core.consumer;

import com.stockstream.core.config.ConsumerConfig;
import com.stockstream.core.model.Tick;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

import java.util.List;
import java.util.function.Consumer;

/**
 * Concrete Kafka consumer for {@link Tick} messages on the tick topic.
 * The handler callback runs once per successfully deserialized tick.
 */
public final class TickConsumer extends AbstractKafkaConsumer<Tick> {

    private final Consumer<Tick> handler;
    private final Integer assignedPartition;

    public TickConsumer(ConsumerConfig config, String topic, String groupId, Consumer<Tick> handler) {
        this(config, topic, groupId, null, handler);
    }

    public TickConsumer(ConsumerConfig config, String topic, String groupId, Integer partition, Consumer<Tick> handler) {
        super(config, topic, groupId, new MessageDeserializer<>(Tick.class));
        if (handler == null) throw new IllegalArgumentException("handler is required");
        if (partition != null && partition < 0) throw new IllegalArgumentException("partition must be >= 0");
        this.handler = handler;
        this.assignedPartition = partition;
    }

    @Override
    protected void subscribeToSource(KafkaConsumer<String, byte[]> consumer) {
        if (assignedPartition == null) {
            super.subscribeToSource(consumer);
            return;
        }
        consumer.assign(List.of(new TopicPartition(topic(), assignedPartition)));
    }

    @Override
    protected void process(Tick tick) {
        handler.accept(tick);
    }

    @Override
    protected long getEventTimestamp(Tick message, org.apache.kafka.clients.consumer.ConsumerRecord<String, byte[]> record) {
        if (message.publishedAt() != null) {
            return message.publishedAt().toEpochMilli();
        }
        return super.getEventTimestamp(message, record);
    }
}
