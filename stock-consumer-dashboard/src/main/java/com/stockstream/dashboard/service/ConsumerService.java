package com.stockstream.dashboard.service;

import com.stockstream.core.config.ConfigLoader;
import com.stockstream.core.config.ConsumerConfig;
import com.stockstream.core.config.Subscription;
import com.stockstream.core.consumer.CandleConsumer;
import com.stockstream.core.consumer.NewsConsumer;
import com.stockstream.core.consumer.TickConsumer;
<<<<<<< HEAD
import com.stockstream.core.metrics.ConsumerMetrics;
=======
import com.stockstream.dashboard.store.CandleStore;
>>>>>>> main
import com.stockstream.dashboard.store.NewsStore;
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
    private final NewsStore newsStore;
    private final CandleStore candleStore;
    private final StockWebSocketHandler wsHandler;
<<<<<<< HEAD
    private final CsvMetricsWriter csvWriter;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private TickConsumer tickConsumer;
    private NewsConsumer newsConsumer;
    private ConsumerMetrics tickMetrics;
    private ConsumerMetrics newsMetrics;

    public ConsumerService(TickStore tickStore, NewsStore newsStore, StockWebSocketHandler wsHandler, CsvMetricsWriter csvWriter) {
=======
    private ExecutorService executor;

    private TickConsumer tickConsumer;
    private NewsConsumer newsConsumer;
    private final List<CandleConsumer> candleConsumers = new ArrayList<>();

    public ConsumerService(TickStore tickStore, NewsStore newsStore,
                           CandleStore candleStore, StockWebSocketHandler wsHandler) {
>>>>>>> main
        this.tickStore = tickStore;
        this.newsStore = newsStore;
        this.candleStore = candleStore;
        this.wsHandler = wsHandler;
        this.csvWriter = csvWriter;
    }

    @PostConstruct
    public void start() {
        ConsumerConfig config = ConfigLoader.load();
        Subscription tickSub = ConfigLoader.loadTickSubscription();
        Subscription newsSub = ConfigLoader.loadNewsSubscription();
        List<Subscription> candleSubs = ConfigLoader.loadCandleSubscriptions();

        executor = Executors.newFixedThreadPool(2 + candleSubs.size());

        tickConsumer = new TickConsumer(config, tickSub.topic(), tickSub.groupId(), tick -> {
            tickStore.add(tick);
            csvWriter.writeTickMetrics(tick, System.currentTimeMillis());
            wsHandler.broadcast("tick", tick);
            System.out.printf("[TICK] %s | price=%.2f | vol=%d | lag=%dms%n",
              tick.securityName(), tick.price(), tick.volume(), tick.lagMs());
        });

        newsConsumer = new NewsConsumer(config, newsSub.topic(), newsSub.groupId(), event -> {
            newsStore.add(event);
            wsHandler.broadcast("news", event);
        });

<<<<<<< HEAD
        // Get metrics instances
        tickMetrics = tickConsumer.getMetrics();
        newsMetrics = newsConsumer.getMetrics();
=======
        for (Subscription sub : candleSubs) {
            CandleConsumer cc = new CandleConsumer(config, sub.topic(), sub.groupId(), candle -> {
                candleStore.add(candle);
                wsHandler.broadcast("candle", candle);
            });
            candleConsumers.add(cc);
        }
>>>>>>> main

        executor.submit(tickConsumer::start);
        executor.submit(newsConsumer::start);
        for (CandleConsumer cc : candleConsumers) {
            executor.submit(cc::start);
        }
    }

    @PreDestroy
    public void stop() {
        if (tickConsumer != null) tickConsumer.shutdown();
        if (newsConsumer != null) newsConsumer.shutdown();
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

    public ConsumerMetrics getNewsMetrics() {
        return newsMetrics;
    }
}
