package examples;

import com.stockstream.core.validation.TickValidator;
import com.stockstream.core.validation.ValidationResult;

public class ValidationDemo {

    public static void main(String[] args) {
        System.out.println("=== Validation System Demo ===\n");

        // Create validator
        TickValidator validator = new TickValidator();

        // SIMULATED SCENARIO: Tick IDs received for AAPL
        System.out.println("Simulating AAPL tick reception:");
        System.out.println("Received tick IDs: 1, 2, 3, 5, 6, 10");
        System.out.println("Missing tick IDs: 4, 7, 8, 9\n");

        // Record ticks (simulating what happens in TickStore.add())
        long[] receivedTicks = {1, 2, 3, 5, 6, 10};
        for (long tickId : receivedTicks) {
            validator.recordTick(createMockTick(tickId, "AAPL"));
            System.out.println("✓ Recorded tick #" + tickId);
        }

        // Perform validation
        System.out.println("\n🔍 Running validation...");
        ValidationResult result = validator.validateSymbol("AAPL");

        // Display results
        System.out.println("\n📊 VALIDATION RESULTS:");
        System.out.println("======================");
        System.out.println("Symbol: " + result.symbol());
        System.out.println("Expected range: 1 to " + result.expectedCount());
        System.out.println("Actual received: " + result.actualCount());
        System.out.println("Missing count: " + result.missingCount());
        System.out.println("Completeness: " + String.format("%.2f", result.completenessPercentage()) + "%");
        System.out.println("Complete: " + result.isComplete());

        if (!result.gaps().isEmpty()) {
            System.out.println("\n🔴 GAPS DETECTED:");
            result.gaps().forEach(gap ->
                System.out.println("  • " + gap + " (missing " + gap.gapSize() + " ticks)"));
        }

        // Explanation of the validation logic
        System.out.println("\n📖 HOW IT WORKS:");
        System.out.println("================");
        System.out.println("1. Track min/max tick IDs received");
        System.out.println("   → Min: " + 1 + ", Max: " + 10);
        System.out.println("2. Calculate expected range");
        System.out.println("   → Expected: 10 ticks (IDs 1-10)");
        System.out.println("3. Check which IDs are missing");
        System.out.println("   → Received: " + result.actualCount() + " ticks");
        System.out.println("   → Missing: " + result.missingCount() + " ticks (4,7,8,9)");
        System.out.println("4. Calculate completeness percentage");
        System.out.println("   → " + result.actualCount() + "/" + result.expectedCount() + " × 100 = " +
                String.format("%.2f", result.completenessPercentage()) + "%");
    }

    // Helper to create mock tick
    private static com.stockstream.core.model.Tick createMockTick(long tickId, String symbol) {
        return new com.stockstream.core.model.Tick(
            tickId,
            symbol + " Corp",
            symbol,
            new java.math.BigDecimal("150.0"),
            1000L,
            java.time.Instant.now(),
            java.time.Instant.now(),
            0
        );
    }
}