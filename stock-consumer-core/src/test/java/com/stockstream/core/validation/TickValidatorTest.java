package com.stockstream.core.validation;

import com.stockstream.core.model.Tick;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TickValidatorTest {

    private Tick createTick(long tickId, String symbol) {
        return new Tick(
            tickId, symbol, symbol, BigDecimal.valueOf(100.0), 1000L,
            Instant.now(), Instant.now(), 0
        );
    }

    @Test
    void testNoGapsValidation() {
        TickValidator validator = new TickValidator();

        // Add ticks 1-5 without gaps
        for (long i = 1; i <= 5; i++) {
            validator.recordTick(createTick(i, "AAPL"));
        }

        ValidationResult result = validator.validateSymbol("AAPL");

        assertEquals("AAPL", result.symbol());
        assertEquals(5, result.expectedCount());
        assertEquals(5, result.actualCount());
        assertEquals(0, result.missingCount());
        assertTrue(result.isComplete());
        assertEquals(100.0, result.completenessPercentage(), 0.01);
        assertEquals(0, result.gaps().size());
    }

    @Test
    void testSingleGapDetection() {
        TickValidator validator = new TickValidator();

        // Add ticks with gap: 1, 2, 3, 5, 6 (missing 4)
        validator.recordTick(createTick(1, "AAPL"));
        validator.recordTick(createTick(2, "AAPL"));
        validator.recordTick(createTick(3, "AAPL"));
        validator.recordTick(createTick(5, "AAPL"));
        validator.recordTick(createTick(6, "AAPL"));

        ValidationResult result = validator.validateSymbol("AAPL");

        assertEquals(6, result.expectedCount());
        assertEquals(5, result.actualCount());
        assertEquals(1, result.missingCount());
        assertFalse(result.isComplete());
        assertEquals(5.0 / 6.0 * 100.0, result.completenessPercentage(), 0.01);
        assertEquals(1, result.gaps().size());
        assertEquals(4, result.gaps().get(0).startId());
        assertEquals(4, result.gaps().get(0).endId());
        assertEquals(1, result.gaps().get(0).gapSize());
    }

    @Test
    void testMultipleGapsDetection() {
        TickValidator validator = new TickValidator();

        // Add ticks: 1, 2, 5, 6, 10 (missing 3,4,7,8,9)
        validator.recordTick(createTick(1, "AAPL"));
        validator.recordTick(createTick(2, "AAPL"));
        validator.recordTick(createTick(5, "AAPL"));
        validator.recordTick(createTick(6, "AAPL"));
        validator.recordTick(createTick(10, "AAPL"));

        ValidationResult result = validator.validateSymbol("AAPL");

        assertEquals(10, result.expectedCount());
        assertEquals(5, result.actualCount());
        assertEquals(5, result.missingCount());
        assertFalse(result.isComplete());
        assertEquals(50.0, result.completenessPercentage(), 0.01);
        assertEquals(2, result.gaps().size());

        // Check first gap (3-4)
        ValidationResult.Gap firstGap = result.gaps().get(0);
        assertEquals(3, firstGap.startId());
        assertEquals(4, firstGap.endId());
        assertEquals(2, firstGap.gapSize());

        // Check second gap (7-9)
        ValidationResult.Gap secondGap = result.gaps().get(1);
        assertEquals(7, secondGap.startId());
        assertEquals(9, secondGap.endId());
        assertEquals(3, secondGap.gapSize());
    }

    @Test
    void testMultipleSymbols() {
        TickValidator validator = new TickValidator();

        // AAPL: 1, 2, 3, 4, 5 (no gaps)
        for (long i = 1; i <= 5; i++) {
            validator.recordTick(createTick(i, "AAPL"));
        }

        // GOOG: 1, 2, 4, 5 (missing 3)
        validator.recordTick(createTick(1, "GOOG"));
        validator.recordTick(createTick(2, "GOOG"));
        validator.recordTick(createTick(4, "GOOG"));
        validator.recordTick(createTick(5, "GOOG"));

        Map<String, ValidationResult> results = validator.validateAll();

        assertEquals(2, results.size());

        ValidationResult aaplResult = results.get("AAPL");
        assertTrue(aaplResult.isComplete());
        assertEquals(5, aaplResult.actualCount());
        assertEquals(0, aaplResult.missingCount());

        ValidationResult googResult = results.get("GOOG");
        assertFalse(googResult.isComplete());
        assertEquals(4, googResult.actualCount());
        assertEquals(1, googResult.missingCount());
    }

    @Test
    void testEmptyValidation() {
        TickValidator validator = new TickValidator();

        ValidationResult result = validator.validateSymbol("AAPL");

        assertEquals("AAPL", result.symbol());
        assertEquals(0, result.expectedCount());
        assertEquals(0, result.actualCount());
        assertEquals(0, result.missingCount());
        assertTrue(result.isComplete());
        assertEquals(100.0, result.completenessPercentage(), 0.01);
        assertEquals(0, result.gaps().size());
    }

    @Test
    void testResetFunctionality() {
        TickValidator validator = new TickValidator();

        validator.recordTick(createTick(1, "AAPL"));
        validator.recordTick(createTick(2, "AAPL"));

        ValidationResult before = validator.validateSymbol("AAPL");
        assertEquals(2, before.actualCount());

        validator.resetSymbol("AAPL");

        ValidationResult after = validator.validateSymbol("AAPL");
        assertEquals(0, after.actualCount());

        // Add new data
        validator.recordTick(createTick(10, "AAPL"));
        ValidationResult newResult = validator.validateSymbol("AAPL");
        assertEquals(1, newResult.actualCount());
        assertEquals(10, newResult.expectedCount());
        assertEquals(9, newResult.missingCount());
    }

    @Test
    void testCaseInsensitiveSymbolHandling() {
        TickValidator validator = new TickValidator();

        validator.recordTick(createTick(1, "aapl"));
        validator.recordTick(createTick(2, "AAPL"));
        validator.recordTick(createTick(3, "Aapl"));

        ValidationResult result = validator.validateSymbol("AAPL");

        assertEquals(3, result.actualCount());
        assertEquals(3, result.expectedCount());
        assertEquals(1, validator.getTrackedSymbols().size());
    }

    @Test
    void testGapLimit() {
        TickValidator validator = new TickValidator(2); // Limit to 2 gaps

        // Create multiple gaps
        for (long i = 1; i <= 10; i += 3) { // 1, 4, 7, 10
            validator.recordTick(createTick(i, "AAPL"));
        }

        ValidationResult result = validator.validateSymbol("AAPL");

        // Should find only 2 gaps due to limit
        assertTrue(result.gaps().size() <= 2);
        assertTrue(result.missingCount() > 0);
    }
}