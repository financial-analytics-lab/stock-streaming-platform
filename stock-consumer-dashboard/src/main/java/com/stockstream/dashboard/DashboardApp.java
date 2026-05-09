package com.stockstream.dashboard;

import com.stockstream.core.config.ConfigLoader;
import com.stockstream.core.config.ConsumerConfig;
import com.stockstream.core.config.Subscription;
import com.stockstream.core.consumer.TickConsumer;
import com.stockstream.core.model.Tick;

public class DashboardApp {

    public static void main(String[] args) {
        ConsumerConfig config = ConfigLoader.load();
        Subscription tick = ConfigLoader.loadTickSubscription();

        try (TickConsumer consumer = new TickConsumer(config, tick.topic(), tick.groupId(),
                DashboardApp::printTick)) {
            Runtime.getRuntime().addShutdownHook(consumer.shutdownHook());
            consumer.start();
        }
    }

    // TODO: push tick to WebSocket/SSE for frontend
    private static void printTick(Tick tick) {
        System.out.printf("[%s] $%s | vol=%d | %s%n | %s%n | %s%n | %s%n",
                tick.symbol(), tick.price(), tick.volume(), tick.timestamp(),
                tick.publishedAt(), tick.securityName(), tick.lagMs());
    }
}
