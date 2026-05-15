package com.stockstream.dashboard.store;

import com.stockstream.core.model.Candle;
import com.stockstream.core.model.CandleStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for OHLCV candles, keyed by (symbol, interval).
 *
 * Each interval maintains:
 *   - a rolling history of CLOSED candles (capped at MAX_PER_KEY)
 *   - the latest in-progress (OPEN) candle, if any
 *
 * The candle-processor emits both flavours; OPEN updates morph the live
 * bar in place, CLOSED updates finalize it and roll into history.
 */
@Component
public class CandleStore {

    private static final int MAX_PER_KEY = 500;

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ArrayDeque<Candle>>> closedHistory =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Candle>> liveByInterval =
            new ConcurrentHashMap<>();

    public void add(Candle candle) {
        if (candle == null || candle.symbol() == null || candle.interval() == null) return;
        String symbol = candle.symbol();
        String interval = candle.interval();

        if (candle.status() == CandleStatus.CLOSED) {
            ArrayDeque<Candle> deque = closedHistory
                    .computeIfAbsent(symbol, s -> new ConcurrentHashMap<>())
                    .computeIfAbsent(interval, i -> new ArrayDeque<>(MAX_PER_KEY + 1));
            synchronized (deque) {
                // If a CLOSED for the same window already exists, replace it; otherwise append.
                Iterator<Candle> it = deque.descendingIterator();
                boolean replaced = false;
                int idxFromEnd = 0;
                while (it.hasNext()) {
                    Candle existing = it.next();
                    if (existing.windowStart() != null && existing.windowStart().equals(candle.windowStart())) {
                        // Replace in place: convert deque to list, swap, rebuild.
                        replaceInDeque(deque, deque.size() - 1 - idxFromEnd, candle);
                        replaced = true;
                        break;
                    }
                    idxFromEnd++;
                }
                if (!replaced) {
                    deque.addLast(candle);
                    while (deque.size() > MAX_PER_KEY) deque.removeFirst();
                }
            }
            // Clear matching live entry so the UI stops drawing the partial bar.
            ConcurrentHashMap<String, Candle> liveBySymbol = liveByInterval.get(symbol);
            if (liveBySymbol != null) {
                liveBySymbol.compute(interval, (k, current) -> {
                    if (current != null && current.windowStart() != null
                            && current.windowStart().equals(candle.windowStart())) {
                        return null;
                    }
                    return current;
                });
            }
        } else { // OPEN
            liveByInterval
                    .computeIfAbsent(symbol, s -> new ConcurrentHashMap<>())
                    .put(interval, candle);
        }
    }

    public List<Candle> getHistory(String symbol, String interval, int limit) {
        ConcurrentHashMap<String, ArrayDeque<Candle>> byInterval = closedHistory.get(symbol);
        if (byInterval == null) return List.of();
        ArrayDeque<Candle> deque = byInterval.get(interval);
        if (deque == null) return List.of();
        synchronized (deque) {
            int skip = Math.max(0, deque.size() - limit);
            return deque.stream().skip(skip).toList();
        }
    }

    public Candle getLive(String symbol, String interval) {
        ConcurrentHashMap<String, Candle> byInterval = liveByInterval.get(symbol);
        if (byInterval == null) return null;
        return byInterval.get(interval);
    }

    private static void replaceInDeque(ArrayDeque<Candle> deque, int targetIndex, Candle replacement) {
        List<Candle> snapshot = new ArrayList<>(deque);
        snapshot.set(targetIndex, replacement);
        deque.clear();
        deque.addAll(snapshot);
    }
}
