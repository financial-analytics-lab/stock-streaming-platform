package com.stockstream.dashboard.model;

import com.stockstream.core.model.Tick;

import java.time.Instant;
import java.util.*;

/**
 *  Per-symbol in-memory tick buffer.
 * Data structure: TreeMap<Instant, Tick>
 *   - Keys are publishedAt timestamps, naturally sorted ascending
 *   - subMap(from, to)          → O(log n) range query
 *   - headMap(cutoff).clear()   → O(log n) time eviction on every write
 *   - lastEntry()               → O(log n) latest tick
 *
 * Concurrency: volatile snapshot publication
 *   - Single writer guaranteed by Kafka partition assignment
 *   - Readers access a volatile BufferState — completely lock-free
 *   - Writer publishes snapshot and latest as one atomic BufferState reference
 *
 * Eviction priority:
 *   Tier 3 (old session ticks)  → evicted first under heap pressure
 *   Tier 2 (warm window ticks)  → evicted second under heap pressure
 *   Tier 1 (latest tick)        → NEVER evicted
 **/

public final class SymbolBuffer {
    private static final java.time.Duration WARM_WINDOW = java.time.Duration.ofMinutes(15);
    private static final double HEAP_PRESSURE_THRESHOLD = 0.15;

    private final TreeMap<Instant, Tick> ticks = new TreeMap<>();


    private record BufferState(List<Tick> snapshot, Tick latest) {}
    private volatile BufferState state = new BufferState(List.of(), null);



    /**
    * write to memory first then check eviction
    * */
    public void add(Tick tick) {
        ticks.put(tick.publishedAt(), tick);
        evictByHeapIfNeeded();
        state = new BufferState(
                List.copyOf(ticks.values()),
                ticks.lastEntry().getValue()
        );
    }

    /**
     * Full session load — called when a client opens for first time.
     * Returns all ticks from session open to now.
     */
    public List<Tick> fullSessionLoad() {
        return state.snapshot();
    }


    /**
     *  Range query — used by Tick Provider for range queries.
     */
    public List<Tick> getRange(Instant from, Instant to) {
        return state.snapshot().stream().filter(t-> !t.publishedAt().isBefore(from)
                                            && !t.publishedAt().isAfter(to)).toList();
    }

    /**
     * Hot cache -- Latest Tick
     * */
    public Tick getLatest() {
        return state.latest();
    }


    /**
    *       EVICTION POLICY
    **/

    private void evictByHeapIfNeeded(){
        if(!isHeapUnderPressure()) return;

        Instant latestKey = ticks.lastKey();

        // check for old generation (tier 3)
        Instant warmBound = Instant.now().minus(WARM_WINDOW);
        ticks.headMap(warmBound, false).clear();

        if (!isHeapUnderPressure()) return;

        // Tier 2 — evict half the remaining warm window entries, keeping latest

        int toEvict = (ticks.size() - 1) / 2;
        Iterator<Instant> it = ticks.keySet().iterator();
        while (toEvict-- > 0 && it.hasNext()) {
            Instant key = it.next();
            if (key.equals(latestKey)) break;
            it.remove();
        }

    }

    private static boolean isHeapUnderPressure() {
        Runtime rt = Runtime.getRuntime();
        long usable = rt.freeMemory() + (rt.maxMemory() - rt.totalMemory());
        return (double) usable / rt.maxMemory() < HEAP_PRESSURE_THRESHOLD;
    }

    public boolean isEmpty() { return ticks.isEmpty(); }

    public Instant getEarliestTimestamp() { return ticks.firstKey(); }

    public void clearAll() {
        ticks.clear();
        state = new BufferState(List.of(), null);
    }
}
