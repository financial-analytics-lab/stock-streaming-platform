package com.stockstream.dashboard.store;

import com.stockstream.core.model.Tick;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Component
public class TickStore {

    private static final int MAX_PER_SYMBOL = 500;

    private final ConcurrentHashMap<String, ArrayDeque<Tick>> store = new ConcurrentHashMap<>();
    private final AtomicLong totalReceived = new AtomicLong();
    private final LongAdder lagSum = new LongAdder();

    public void add(Tick tick) {
        totalReceived.incrementAndGet();
        lagSum.add(tick.lagMs());
        store.compute(tick.symbol(), (k, deque) -> {
            if (deque == null) deque = new ArrayDeque<>(MAX_PER_SYMBOL + 1);
            deque.addLast(tick);
            if (deque.size() > MAX_PER_SYMBOL) deque.removeFirst();
            return deque;
        });
    }

    public List<Tick> getHistory(String symbol, int limit) {
        ArrayDeque<Tick> deque = store.get(symbol.toUpperCase());
        if (deque == null) return List.of();
        synchronized (deque) {
            int skip = Math.max(0, deque.size() - limit);
            return deque.stream().skip(skip).toList();
        }
    }

    public Map<String, Tick> getLatestPerSymbol() {
        Map<String, Tick> result = new HashMap<>();
        store.forEach((symbol, deque) -> {
            synchronized (deque) {
                if (!deque.isEmpty()) result.put(symbol, deque.peekLast());
            }
        });
        return result;
    }

    public Set<String> getSymbols() {
        return Collections.unmodifiableSet(store.keySet());
    }

    public long getTotalReceived() {
        return totalReceived.get();
    }

    /** Max event-time across the latest tick of each symbol — the current simulated "now". */
    public Optional<Instant> currentSimulationTime() {
        Instant max = null;
        for (ArrayDeque<Tick> deque : store.values()) {
            Tick last;
            synchronized (deque) {
                last = deque.peekLast();
            }
            if (last == null) continue;
            Instant t = last.timestamp();
            if (t != null && (max == null || t.isAfter(max))) max = t;
        }
        return Optional.ofNullable(max);
    }

    public double getAverageLagMs() {
        long total = totalReceived.get();
        return total == 0 ? 0.0 : (double) lagSum.sum() / total;
    }
}
