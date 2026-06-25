package com.stockstream.dashboard.service;

import com.stockstream.core.config.ConfigLoader;
import com.stockstream.core.config.ConsumerConfig;
import com.stockstream.core.config.Subscription;
import com.stockstream.core.consumer.CandleConsumer;
import com.stockstream.core.consumer.TickConsumerGroup;
import com.stockstream.core.metrics.ConsumerMetrics;
import com.stockstream.dashboard.store.CandleStore;
import com.stockstream.dashboard.store.TickStore;
import com.stockstream.dashboard.websocket.StockWebSocketHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class ConsumerService {

    private final TickStore tickStore;
    private final CandleStore candleStore;
    private final StockWebSocketHandler wsHandler;
    private final CsvMetricsWriter csvWriter;
    private ExecutorService executor;

    private TickConsumerGroup tickConsumerGroup;
    private ConsumerMetrics tickMetrics;
    private final List<CandleConsumer> candleConsumers = new ArrayList<>();

    public ConsumerService(TickStore tickStore,
                           CandleStore candleStore, StockWebSocketHandler wsHandler, CsvMetricsWriter csvWriter) {
        this.tickStore = tickStore;
        this.candleStore = candleStore;
        this.wsHandler = wsHandler;
        this.csvWriter = csvWriter;
    }

    @PostConstruct
    public void start() {
        ConsumerConfig config = ConfigLoader.load();
        Subscription tickSub = ConfigLoader.loadTickSubscription();
        List<Subscription> candleSubs = ConfigLoader.loadCandleSubscriptions();
        int tickPartitions = ConfigLoader.loadTickPartitionCount();

        executor = Executors.newFixedThreadPool(candleSubs.size());

        // Handle Ticks (parallel: 1 consumer per partition)
        tickConsumerGroup = new TickConsumerGroup(
                config, tickSub.topic(), tickSub.groupId(), tickPartitions,
                tick -> {
                    tickStore.add(tick);
                    csvWriter.writeTickMetrics(tick, System.currentTimeMillis());
                    wsHandler.broadcast("tick", tick);
                    System.out.printf("[TICK] %s | price=%.2f | vol=%d | lag=%dms%n",
                            tick.securityName(), tick.price(), tick.volume(), tick.lagMs());
                },
                null
        );

        // Handle candles
        for (Subscription sub : candleSubs) {
            CandleConsumer cc = new CandleConsumer(config, sub.topic(), sub.groupId(), candle -> {
                candleStore.add(candle);
                wsHandler.broadcast("candle", candle);
            });
            candleConsumers.add(cc);
        }

        tickMetrics = tickConsumerGroup.getMetrics();

        for (CandleConsumer cc : candleConsumers) {
            executor.submit(cc::start);
        }
    }

    @PreDestroy
    public void stop() {
        if (tickConsumerGroup != null) tickConsumerGroup.close();
        for (CandleConsumer cc : candleConsumers) cc.shutdown();
        if (executor != null) {
            executor.shutdown();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public ConsumerMetrics getTickMetrics() {
        return tickMetrics;
    }
}
