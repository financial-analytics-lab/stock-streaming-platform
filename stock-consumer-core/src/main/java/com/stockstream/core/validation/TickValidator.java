package com.stockstream.core.validation;

import com.stockstream.core.model.Tick;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Validates tick data completeness by tracking tick_id sequences per symbol.
 * Detects gaps in tick sequences and calculates completeness percentage.
 */
public class TickValidator {

    private final Map<String, SymbolValidationTracker> trackers = new ConcurrentHashMap<>();
    private static long maxGapsToReport = 0;

    public TickValidator() {
        this(100); // Default: report up to 100 gaps
    }

    public TickValidator(int maxGapsToReport) {
        this.maxGapsToReport = maxGapsToReport;
    }

    /**
     * Record a tick for validation tracking.
     */
    public void recordTick(Tick tick) {
        String symbol = tick.symbol().toUpperCase();
        trackers.computeIfAbsent(symbol, k -> new SymbolValidationTracker())
                .recordTick(tick.tickId());
    }

    /**
     * Get validation result for a specific symbol.
     */
    public ValidationResult validateSymbol(String symbol) {
        SymbolValidationTracker tracker = trackers.get(symbol.toUpperCase());
        if (tracker == null) {
            return new ValidationResult(
                    symbol, 0, 0, 0, List.of(), 100.0, Instant.now()
            );
        }
        return tracker.getValidationResult(symbol.toUpperCase());
    }

    /**
     * Get validation results for all symbols.
     */
    public Map<String, ValidationResult> validateAll() {
        return trackers.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().getValidationResult(e.getKey())
                ));
    }

    /**
     * Get symbols being tracked.
     */
    public Set<String> getTrackedSymbols() {
        return Collections.unmodifiableSet(trackers.keySet());
    }

    /**
     * Reset validation tracking for a specific symbol.
     */
    public void resetSymbol(String symbol) {
        trackers.remove(symbol.toUpperCase());
    }

    /**
     * Reset all validation tracking.
     */
    public void resetAll() {
        trackers.clear();
    }

    /**
     * Tracker for individual symbol validation.
     */
    private static class SymbolValidationTracker {
        private final AtomicLong minTickId = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxTickId = new AtomicLong(Long.MIN_VALUE);
        private final AtomicLong totalCount = new AtomicLong(0);
        private final Set<Long> receivedTickIds = ConcurrentHashMap.newKeySet();

        public void recordTick(long tickId) {
            // Update min/max bounds
            updateMin(minTickId, tickId);
            updateMax(maxTickId, tickId);
            totalCount.incrementAndGet();
            receivedTickIds.add(tickId);
        }

        private void updateMin(AtomicLong minRef, long value) {
            long currentMin;
            do {
                currentMin = minRef.get();
                if (value >= currentMin) break;
            } while (!minRef.compareAndSet(currentMin, value));
        }

        private void updateMax(AtomicLong maxRef, long value) {
            long currentMax;
            do {
                currentMax = maxRef.get();
                if (value <= currentMax) break;
            } while (!maxRef.compareAndSet(currentMax, value));
        }

        public ValidationResult getValidationResult(String symbol) {
            long min = minTickId.get();
            long max = maxTickId.get();
            long actualCount = totalCount.get();

            if (actualCount == 0) {
                return new ValidationResult(symbol, 0, 0, 0, List.of(), 100.0, Instant.now());
            }

            // Calculate expected range
            long expectedCount = max - min + 1;

            // Find gaps
            List<ValidationResult.Gap> gaps = findGaps(min, max);

            long missingCount = gaps.stream()
                    .mapToLong(ValidationResult.Gap::gapSize)
                    .sum();

            double completeness = expectedCount > 0 ?
                    ((double) (expectedCount - missingCount) / expectedCount) * 100.0 : 100.0;

            return new ValidationResult(
                    symbol, expectedCount, actualCount, missingCount,
                    gaps, completeness, Instant.now()
            );
        }

        private List<ValidationResult.Gap> findGaps(long min, long max) {
            if (min == Long.MAX_VALUE || max == Long.MIN_VALUE) {
                return List.of();
            }

            List<ValidationResult.Gap> gaps = new ArrayList<>();
            long gapStart = -1;

            for (long id = min; id <= max; id++) {
                if (!receivedTickIds.contains(id)) {
                    if (gapStart == -1) {
                        gapStart = id;
                    }
                } else {
                    if (gapStart != -1) {
                        long gapEnd = id - 1;
                        gaps.add(new ValidationResult.Gap(gapStart, gapEnd, gapEnd - gapStart + 1));
                        if (gaps.size() >= maxGapsToReport) {
                            break;
                        }
                        gapStart = -1;
                    }
                }
            }

            // Handle gap at the end
            if (gapStart != -1 && gaps.size() < maxGapsToReport) {
                gaps.add(new ValidationResult.Gap(gapStart, max, max - gapStart + 1));
            }

            return gaps;
        }
    }
}