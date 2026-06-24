package com.stockstream.dashboard.service;

import com.stockstream.core.model.Tick;
import com.stockstream.dashboard.store.TickStorageReader;
import com.stockstream.dashboard.store.TickStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Service
public class TickProvider {
    private static final Logger log = Logger.getLogger(TickProvider.class.getName());

    private final TickStore memoryCache;
    private final TickStorageReader diskStorage;

    public TickProvider(TickStore memoryCache, TickStorageReader diskStorage) {
        this.memoryCache = memoryCache;
        this.diskStorage = diskStorage;
    }

    /**
     * Providing ticks either from memory or disk or both depending on the query range.
    * */
    public List<Tick> getTicks(String symbol, Instant start, Instant end) {
        Instant oldestInMemory = memoryCache.getOldestAvailableTime(symbol);

        // Historical ticks -> should all be provided from disk
        if (oldestInMemory == null || end.isBefore(oldestInMemory)){
            log.fine("Routing query to DISK for: " + symbol);
            return diskStorage.getRangeFromDisk(symbol, start, end);
        }

        // During trading session -> range is contained within memory
        if(!start.isBefore(oldestInMemory)) {
            log.fine("Routing query to MEMORY for: " + symbol);
            return memoryCache.getRange(symbol, start, end);
        }

        // Data starts on disk, but ends in memory. We must fetch from both and aggregate.

        Instant diskEndTarget = oldestInMemory.minusMillis(1);
        List<Tick> diskTicks = diskStorage.getRangeFromDisk(symbol, start, diskEndTarget);

        List<Tick> memoryTicks = memoryCache.getRange(symbol, oldestInMemory, end);

        List<Tick> aggregatedResult = new ArrayList<>(diskTicks.size() + memoryTicks.size());
        aggregatedResult.addAll(diskTicks);
        aggregatedResult.addAll(memoryTicks);

        return aggregatedResult;
    }

    public List<Tick> getHistoricalTicks(String symbol, Instant start, Instant end) {
        return diskStorage.getRangeFromDisk(symbol, start, end);
    }

}
