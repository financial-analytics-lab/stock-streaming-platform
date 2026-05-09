package com.stockstream.dashboard.store;

import com.stockstream.core.model.Tick;
import com.stockstream.core.validation.TickValidator;
import com.stockstream.core.validation.ValidationResult;
import org.springframework.stereotype.Component;

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
    private final TickValidator validator = new TickValidator();

    public void add(Tick tick) {
        totalReceived.incrementAndGet();
        lagSum.add(tick.lagMs());

        // Store the tick
        store.compute(tick.symbol(), (k, deque) -> {
            if (deque == null) deque = new ArrayDeque<>(MAX_PER_SYMBOL + 1);
            deque.addLast(tick);
            if (deque.size() > MAX_PER_SYMBOL) deque.removeFirst();
            return deque;
        });

        // Record for validation
        validator.recordTick(tick);
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

    public double getAverageLagMs() {
        long total = totalReceived.get();
        return total == 0 ? 0.0 : (double) lagSum.sum() / total;
    }

    public ValidationResult validateSymbol(String symbol) {
        return validator.validateSymbol(symbol);
    }

    public Map<String, ValidationResult> validateAll() {
        return validator.validateAll();
    }

    public Set<String> getTrackedSymbols() {
        return validator.getTrackedSymbols();
    }

    public void resetValidation(String symbol) {
        validator.resetSymbol(symbol);
    }

    public void resetAllValidation() {
        validator.resetAll();
    }
}
