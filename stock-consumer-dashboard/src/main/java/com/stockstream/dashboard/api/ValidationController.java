package com.stockstream.dashboard.api;

import com.stockstream.dashboard.store.TickStore;
import com.stockstream.core.validation.ValidationResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/validation")
@CrossOrigin(origins = "*")
public class ValidationController {

    private final TickStore tickStore;

    public ValidationController(TickStore tickStore) {
        this.tickStore = tickStore;
    }

    @GetMapping("/symbols")
    public ResponseEntity<Set<String>> getTrackedSymbols() {
        return ResponseEntity.ok(tickStore.getTrackedSymbols());
    }

    @GetMapping
    public ResponseEntity<Map<String, ValidationResult>> validateAll() {
        return ResponseEntity.ok(tickStore.validateAll());
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<ValidationResult> validateSymbol(@PathVariable String symbol) {
        ValidationResult result = tickStore.validateSymbol(symbol);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{symbol}/summary")
    public ResponseEntity<Map<String, Object>> getValidationSummary(@PathVariable String symbol) {
        ValidationResult result = tickStore.validateSymbol(symbol);

        var summary = Map.of(
            "symbol", symbol,
            "isComplete", result.isComplete(),
            "completenessPercentage", result.completenessPercentage(),
            "expectedCount", result.expectedCount(),
            "actualCount", result.actualCount(),
            "missingCount", result.missingCount(),
            "gapCount", result.gaps().size(),
            "validationTime", result.validationTime()
        );

        return ResponseEntity.ok(summary);
    }

    @PostMapping("/{symbol}/reset")
    public ResponseEntity<String> resetValidation(@PathVariable String symbol) {
        tickStore.resetValidation(symbol);
        return ResponseEntity.ok("Validation reset for symbol: " + symbol);
    }

    @PostMapping("/reset-all")
    public ResponseEntity<String> resetAllValidation() {
        tickStore.resetAllValidation();
        return ResponseEntity.ok("All validation data reset");
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getValidationHealth() {
        Map<String, ValidationResult> allResults = tickStore.validateAll();

        long totalSymbols = allResults.size();
        long completeSymbols = allResults.values().stream()
                .filter(ValidationResult::isComplete)
                .count();

        double avgCompleteness = allResults.values().stream()
                .mapToDouble(ValidationResult::completenessPercentage)
                .average()
                .orElse(100.0);

        boolean isHealthy = avgCompleteness > 99.0; // 99% threshold

        var health = Map.of(
            "isHealthy", isHealthy,
            "totalSymbols", totalSymbols,
            "completeSymbols", completeSymbols,
            "incompleteSymbols", totalSymbols - completeSymbols,
            "averageCompleteness", avgCompleteness,
            "timestamp", System.currentTimeMillis()
        );

        return ResponseEntity.ok(health);
    }
}