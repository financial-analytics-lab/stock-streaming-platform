package com.stockstream.dashboard.api;

import com.stockstream.core.metrics.ConsumerMetrics;
import com.stockstream.core.metrics.MetricsSnapshot;
import com.stockstream.core.metrics.MetricsRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
@CrossOrigin(origins = "*")
public class MetricsController {

    private final MetricsRegistry metricsRegistry;

    public MetricsController() {
        this.metricsRegistry = MetricsRegistry.getInstance();
    }

    @GetMapping
    public ResponseEntity<Map<String, MetricsSnapshot>> getAllMetrics() {
        return ResponseEntity.ok(metricsRegistry.getAllMetrics());
    }

    @GetMapping("/{consumerName}")
    public ResponseEntity<MetricsSnapshot> getMetrics(@PathVariable String consumerName) {
        ConsumerMetrics metrics = metricsRegistry.getMetrics(consumerName);
        if (metrics == null) {
            return ResponseEntity.notFound().build();
        }
        MetricsSnapshot snapshot = metrics.getSnapshot();
        return ResponseEntity.ok(snapshot);
    }

    @PostMapping("/{consumerName}/reset")
    public ResponseEntity<String> resetMetrics(@PathVariable String consumerName) {
        metricsRegistry.resetMetrics(consumerName);
        return ResponseEntity.ok("Metrics reset for consumer: " + consumerName);
    }

    @PostMapping("/reset-all")
    public ResponseEntity<String> resetAllMetrics() {
        metricsRegistry.resetAllMetrics();
        return ResponseEntity.ok("All metrics reset");
    }
}