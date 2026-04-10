package com.stockstream.dashboard;

import com.stockstream.core.config.ConfigLoader;
import com.stockstream.core.config.ConsumerConfig;
import com.stockstream.core.consumer.AbstractTickConsumer;
import com.stockstream.core.model.Tick;

public class DashboardApp {

    static class DashboardConsumer extends AbstractTickConsumer {

        DashboardConsumer(ConsumerConfig config) {
            super(config);
        }

        @Override
        protected void process(Tick tick) {
            // TODO: push tick to WebSocket/SSE for frontend
            System.out.printf("[%s] $%s | vol=%d | %s%n",
                    tick.symbol(), tick.price(), tick.volume(), tick.timestamp());
        }
    }

    public static void main(String[] args) {
        ConsumerConfig config = ConfigLoader.load();
        try (DashboardConsumer consumer = new DashboardConsumer(config)) {
            Runtime.getRuntime().addShutdownHook(consumer.shutdownHook());
            consumer.start();
        }
    }
}
