package com.stockstream.dashboard.api;

import com.stockstream.core.config.ConfigLoader;
import com.stockstream.core.model.Candle;
import com.stockstream.dashboard.store.CandleStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/candles")
public class CandleController {

    private final CandleStore candleStore;

    public CandleController(CandleStore candleStore) {
        this.candleStore = candleStore;
    }

    @GetMapping("/intervals")
    public List<String> getIntervals() {
        return ConfigLoader.loadCandleIntervals();
    }

    @GetMapping("/{symbol}/{interval}/history")
    public Map<String, Object> getHistory(
            @PathVariable String symbol,
            @PathVariable String interval,
            @RequestParam(defaultValue = "200") int limit) {
        String upper = symbol.toUpperCase();
        List<Candle> history = candleStore.getHistory(upper, interval, Math.min(limit, 500));
        Candle live = candleStore.getLive(upper, interval);
        Map<String, Object> body = new HashMap<>();
        body.put("history", history);
        body.put("live", live);
        return body;
    }
}
