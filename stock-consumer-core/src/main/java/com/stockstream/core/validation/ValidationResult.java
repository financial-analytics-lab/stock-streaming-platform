package com.stockstream.core.validation;

import java.time.Instant;
import java.util.List;

/**
 * Represents the result of a validation check for tick data completeness.
 */
public record ValidationResult(
        String symbol,
        long expectedCount,
        long actualCount,
        long missingCount,
        List<Gap> gaps,
        double completenessPercentage,
        Instant validationTime
) {
    public record Gap(long startId, long endId, long gapSize) {
        @Override
        public String toString() {
            return String.format("Gap[%d-%d] size=%d", startId, endId, gapSize);
        }
    }

    public boolean isComplete() {
        return missingCount == 0;
    }

    @Override
    public String toString() {
        return String.format(
            "ValidationResult[%s]: expected=%d, actual=%d, missing=%d, completeness=%.2f%%, gaps=%d",
            symbol, expectedCount, actualCount, missingCount, completenessPercentage, gaps.size()
        );
    }
}