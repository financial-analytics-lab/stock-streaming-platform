package com.stockstream.dashboard.api;

import com.stockstream.core.model.Tick;
import com.stockstream.dashboard.store.TickStore;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class TickController {

    private final TickStore tickStore;

    public TickController(TickStore tickStore) {
        this.tickStore = tickStore;
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

    @GetMapping("/ticks/metrics")
    public Map<String, Object> getMetrics() {
        return Map.of(
                "totalReceived", tickStore.getTotalReceived(),
                "averageLagMs", tickStore.getAverageLagMs(),
                "symbolCount", tickStore.getSymbols().size()
        );
    }
}
