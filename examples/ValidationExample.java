package examples;

import com.stockstream.core.model.Tick;
import com.stockstream.core.validation.TickValidator;
import com.stockstream.core.validation.ValidationResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Example demonstrating how to use the TickValidator for data completeness validation.
 */
public class ValidationExample {

    public static void main(String[] args) {
        System.out.println("=== Tick Data Validation Example ===\n");

        // Create a validator instance
        TickValidator validator = new TickValidator();

        // Scenario 1: Simulate receiving ticks with some gaps
        System.out.println("Scenario 1: Detecting gaps in tick sequence");
        System.out.println("---------------------------------------------");

        // Simulate AAPL ticks with gaps
        long[] aaplTickIds = {1, 2, 3, 5, 6, 8, 9, 10}; // Missing 4, 7
        for (long tickId : aaplTickIds) {
            Tick tick = createTick(tickId, "AAPL");
            validator.recordTick(tick);
            System.out.println("Received AAPL tick #" + tickId);
        }

        ValidationResult aaplResult = validator.validateSymbol("AAPL");
        System.out.println("\nValidation result for AAPL:");
        printValidationSummary(aaplResult);

        // Scenario 2: Complete sequence
        System.out.println("\n\nScenario 2: Complete tick sequence");
        System.out.println("------------------------------------");

        for (long tickId = 1; tickId <= 5; tickId++) {
            Tick tick = createTick(tickId, "GOOG");
            validator.recordTick(tick);
            System.out.println("Received GOOG tick #" + tickId);
        }

        ValidationResult googResult = validator.validateSymbol("GOOG");
        System.out.println("\nValidation result for GOOG:");
        printValidationSummary(googResult);

        // Scenario 3: Multiple symbols
        System.out.println("\n\nScenario 3: Multiple symbols validation");
        System.out.println("-----------------------------------------");

        // Add some MSFT ticks
        for (long tickId : new long[]{1, 3, 4, 7}) {
            validator.recordTick(createTick(tickId, "MSFT"));
        }

        Map<String, ValidationResult> allResults = validator.validateAll();
        System.out.println("Validation results for all symbols:");
        allResults.forEach((symbol, result) -> {
            System.out.println("\n" + symbol + ":");
            printValidationSummary(result);
        });

        // Scenario 4: Health check
        System.out.println("\n\nScenario 4: System health check");
        System.out.println("--------------------------------");

        long totalSymbols = allResults.size();
        long completeSymbols = allResults.values().stream()
                .filter(ValidationResult::isComplete)
                .count();
        double avgCompleteness = allResults.values().stream()
                .mapToDouble(ValidationResult::completenessPercentage)
                .average()
                .orElse(100.0);

        System.out.printf("Total symbols tracked: %d%n", totalSymbols);
        System.out.printf("Complete symbols: %d%n", completeSymbols);
        System.out.printf("Incomplete symbols: %d%n", totalSymbols - completeSymbols);
        System.out.printf("Average completeness: %.2f%%%n", avgCompleteness);
        System.out.printf("System health: %s%n", avgCompleteness > 99.0 ? "HEALTHY" : "UNHEALTHY");

        // Demonstrate gap analysis
        System.out.println("\n\nGap Analysis Details");
        System.out.println("====================");

        allResults.forEach((symbol, result) -> {
            if (!result.isComplete()) {
                System.out.printf("\nSymbol %s has %d gaps:%n", symbol, result.gaps().size());
                result.gaps().forEach(gap ->
                    System.out.printf("  - %s (missing %d ticks)%n", gap, gap.gapSize()));
            }
        });
    }

    private static Tick createTick(long tickId, String symbol) {
        return new Tick(
            tickId,
            symbol + " Corp",
            symbol,
            BigDecimal.valueOf(100.0 + tickId),
            1000L,
            Instant.now(),
            Instant.now(),
            0
        );
    }

    private static void printValidationSummary(ValidationResult result) {
        System.out.printf("  Expected ticks: %d%n", result.expectedCount());
        System.out.printf("  Received ticks: %d%n", result.actualCount());
        System.out.printf("  Missing ticks: %d%n", result.missingCount());
        System.out.printf("  Completeness: %.2f%%%n", result.completenessPercentage());
        System.out.printf("  Status: %s%n", result.isComplete() ? "COMPLETE" : "INCOMPLETE");

        if (!result.gaps().isEmpty()) {
            System.out.printf("  Gaps detected: %d%n", result.gaps().size());
        }
    }
}