package com.stockstream.dashboard.store;

import com.stockstream.core.model.Tick;
import com.stockstream.core.validation.TickValidator;
import com.stockstream.core.validation.ValidationResult;
import com.stockstream.dashboard.model.SymbolBuffer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * In Memory storage layer
 * 1. Keeps Tick objects in memory during the RETENTION_WINDOW period (the session in EGX lasts for 4 hours)
 * 2. Eviction policy is:
 *  2.1 Time_based => evict everything older than max(sessionStart, now - retentionWindow)
 *  2.2 Memory heap pressure does exceeds a threshold = 15% --> evict the old generation tick objects as the priority is to keep the lates
 *  ticks in memory. (hot cache)
 * Data structures used:
 *
 *
* */



@Component
public class TickStore {

    private final ConcurrentHashMap<String, SymbolBuffer> store = new ConcurrentHashMap<>();
    private final AtomicLong totalReceived = new AtomicLong();
    private final LongAdder lagSum = new LongAdder();
    private final TickValidator validator = new TickValidator();

    public void add(Tick tick) {
        totalReceived.incrementAndGet();
        lagSum.add(tick.lagMs());

        // Store the tick
        store.computeIfAbsent(tick.symbol().toUpperCase(), k -> new SymbolBuffer()).add(tick);

        // Record for validation
        validator.recordTick(tick);
    }

    /**
     * Called when a dashboard opens for a symbol.
     * Returns the full session history instantly from memory — no disk hit.
     */
    public List<Tick> getHistory(String symbol, int limit) {
        SymbolBuffer buf = store.get(symbol.toUpperCase());
        return buf == null ? List.of():buf.fullSessionLoad();
    }

    /**
     * Arbitrary range query — used by Tick Provider.
     */
    public List<Tick> getRange(String symbol, Instant from, Instant to) {
        SymbolBuffer buf = store.get(symbol.toUpperCase());
        return buf == null ? List.of() : buf.getRange(from, to);
    }

    public Tick getLatest(String symbol) {
        SymbolBuffer buf = store.get(symbol.toUpperCase());
        return buf == null? null:buf.getLatest();
    }

    public Map<String, Tick> getLatestPerSymbol() {
        Map<String, Tick> result = new HashMap<>();
        store.forEach((symbol, buf) -> {
            Tick latest = buf.getLatest();
            if (latest != null) result.put(symbol, latest);
        });
        return result;
    }

    public Set<String> getSymbols() {
        return Collections.unmodifiableSet(store.keySet());
    }


    public Instant getOldestAvailableTime(String symbol) {
        // Assuming 'ticks' is your TreeMap inside SymbolBuffer
        SymbolBuffer buffer = store.get(symbol);
        if (buffer == null || buffer.isEmpty()) {
            return null; // Nothing in memory
        }
        // TreeMap.firstKey() is O(1)
        return buffer.getEarliestTimestamp();
    }

    @Scheduled(cron = "0 5 14 * * MON-FRI", zone = "Africa/Cairo")
    public void clearPostTradingSession() {
        store.forEach((symbol, buf) -> buf.clearAll());
    }

    public long getTotalReceived() {
        return totalReceived.get();
    }

    /** Max event-time across the latest tick of each symbol — the current simulated "now". */
    public Optional<Instant> currentSimulationTime() {
        Instant max = null;
        for (SymbolBuffer buf : store.values()) {
            Tick last = buf.getLatest();
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
