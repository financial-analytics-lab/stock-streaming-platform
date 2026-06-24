package com.stockstream.dashboard.api;

import com.stockstream.core.model.Tick;
import com.stockstream.dashboard.store.TickStore;
import com.stockstream.dashboard.service.TickProvider;
import org.springframework.web.bind.annotation.*;
 
import java.time.Instant;
import java.util.*;
 
@RestController
@RequestMapping("/api")
public class TickController {
 
    private final TickStore tickStore;
    private final TickProvider tickProvider;
 
    public TickController(TickStore tickStore, TickProvider tickProvider) {
        this.tickStore = tickStore;
        this.tickProvider = tickProvider;
    }

    @GetMapping("/symbols")
    public Collection<Tick> getLatestPerSymbol() {
        return tickStore.getLatestPerSymbol().values();
    }

    @GetMapping("/ticks/latest")
    public Collection<Tick> getLatest() {
        return tickStore.getLatestPerSymbol().values();
    }

    @GetMapping("/ticks/latest/{symbol}")
    public Tick getLatestOfSymbol(@PathVariable String symbol) { return tickStore.getLatest(symbol); }

    @GetMapping("/ticks/{symbol}/history")
    public List<Tick> getHistory(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "200") int limit) {
        return tickStore.getHistory(symbol, Math.min(limit, 500));
    }

    @GetMapping("/ticks/{symbol}/range")
    public List<Tick> getRangeHistorical(
            @PathVariable String symbol,
            @RequestParam String start,
            @RequestParam String end) {
        Instant startTime = Instant.parse(start);
        Instant endTime = Instant.parse(end);
        return tickProvider.getHistoricalTicks(symbol, startTime, endTime);
    }
 
    @GetMapping("/ticks/metrics")
    public Map<String, Object> getMetrics() {
        return Map.of(
                "totalReceived", tickStore.getTotalReceived(),
                "averageLagMs", tickStore.getAverageLagMs(),
                "symbolCount", tickStore.getSymbols().size()
        );
    }
}
