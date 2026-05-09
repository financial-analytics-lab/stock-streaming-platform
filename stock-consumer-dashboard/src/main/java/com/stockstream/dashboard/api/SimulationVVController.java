package com.stockstream.dashboard.api;

import com.stockstream.core.validation.*;
import com.stockstream.dashboard.store.TickStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/vv")
@CrossOrigin(origins = "*")
public class SimulationVVController {

    private final TickStore tickStore;
    private final SimulationVVFramework vvFramework;

    public SimulationVVController(TickStore tickStore) {
        this.tickStore = tickStore;
        this.vvFramework = new SimulationVVFramework();
    }

    /**
     * FACE VALIDITY
     * Expert judgment on model logic and behavior
     */
    @GetMapping("/face-validity")
    public ResponseEntity<Map<String, Object>> getFaceValidity() {
        ValidationResult result = vvFramework.assessFaceValidity();

        return ResponseEntity.ok(Map.of(
                "validationType", "Face Validity",
                "description", "Expert judgment on whether model logic reflects real system behavior",
                "result", result,
                "timestamp", Instant.now()
        ));
    }

    /**
     * HISTORICAL VALIDATION
     * Compare simulated data with known historical patterns
     */
    @PostMapping("/historical-validation")
    public ResponseEntity<Map<String, Object>> validateAgainstHistorical(
            @RequestBody HistoricalValidationRequest request) {

        HistoricalValidationResult result = vvFramework.validateAgainstHistorical(
                request.getHistoricalPatterns(),
                request.getStatisticalProperties());

        return ResponseEntity.ok(Map.of(
                "validationType", "Historical Data Validation",
                "description", "Comparison with known historical stock patterns",
                "matchScore", result.matchScore(),
                "statisticalTests", result.statisticalTests(),
                "timestamp", result.timestamp(),
                "isAcceptable", result.matchScore() >= 80.0
        ));
    }

    /**
     * INTERNAL VALIDATION
     * Check model's internal consistency
     */
    @GetMapping("/internal-validation")
    public ResponseEntity<Map<String, Object>> getInternalValidation() {
        InternalValidationResult result = vvFramework.performInternalValidation();

        return ResponseEntity.ok(Map.of(
                "validationType", "Internal Validation",
                "description", "Model internal consistency and logical behavior",
                "isInternallyConsistent", result.isInternallyConsistent(),
                "anomalies", result.anomalies(),
                "symbolsValidated", result.symbolsValidated(),
                "timestamp", result.timestamp()
        ));
    }

    /**
     * EXTERNAL VALIDATION
     * Compare model outputs with real system behavior
     */
    @PostMapping("/external-validation")
    public ResponseEntity<Map<String, Object>> validateAgainstRealSystem(
            @RequestBody ExternalValidationRequest request) {

        ExternalValidationResult result = vvFramework.validateAgainstRealSystem(
                request.getRealSystemMetrics());

        return ResponseEntity.ok(Map.of(
                "validationType", "External Validation",
                "description", "Comparison with real system performance metrics",
                "credibilityScore", result.credibilityScore(),
                "isCredible", result.isCredible(),
                "comparisons", result.comparisons(),
                "timestamp", result.timestamp()
        ));
    }

    /**
     * SENSITIVITY ANALYSIS
     * Test model response to input parameter variations
     */
    @PostMapping("/sensitivity-analysis")
    public ResponseEntity<Map<String, Object>> performSensitivityAnalysis(
            @RequestBody SensitivityAnalysisRequest request) {

        SensitivityAnalysisResult result = vvFramework.performSensitivityAnalysis(
                request.getInputVariations());

        return ResponseEntity.ok(Map.of(
                "validationType", "Sensitivity Analysis",
                "description", "Analysis of model sensitivity to input parameter changes",
                "sensitivityIndices", result.sensitivityIndices(),
                "highSensitivityParams", result.highSensitivityParams(),
                "mediumSensitivityParams", result.mediumSensitivityParams(),
                "lowSensitivityParams", result.lowSensitivityParams(),
                "timestamp", result.timestamp()
        ));
    }

    /**
     * STATISTICAL VALIDATION
     * Statistical tests on model outputs
     */
    @PostMapping("/statistical-validation")
    public ResponseEntity<Map<String, Object>> performStatisticalValidation(
            @RequestBody StatisticalValidationRequest request) {

        StatisticalValidationResult result = vvFramework.performStatisticalValidation(
                request.getExpectedDistributions());

        return ResponseEntity.ok(Map.of(
                "validationType", "Statistical Validation",
                "description", "Statistical tests comparing model outputs with expected distributions",
                "overallPValue", result.overallPValue(),
                "isStatisticallyValid", result.isStatisticallyValid(),
                "testResults", result.testResults(),
                "timestamp", result.timestamp()
        ));
    }

    /**
     * COMPREHENSIVE V&V REPORT
     * Complete validation and verification assessment
     */
    @PostMapping("/comprehensive-report")
    public ResponseEntity<Map<String, Object>> generateComprehensiveReport(
            @RequestBody ComprehensiveValidationRequest request) {

        ValidationReport report = vvFramework.generateComprehensiveReport(
                request.getHistoricalPatterns(),
                request.getRealSystemMetrics(),
                request.getSensitivityInputs(),
                request.getStatisticalDistributions());

        Map<String, Object> response = new HashMap<>();
        response.put("validationType", "Comprehensive V&V Report");
        response.put("reportId", report.reportId());
        response.put("timestamp", report.timestamp());
        response.put("credibilityScore", report.credibilityScore());
        response.put("isCredible", report.isCredible());
        response.put("grade", report.getGrade());
        response.put("isProductionReady", report.isProductionReady());
        response.put("summary", report.getSummary());
        response.put("recommendations", report.recommendations());
        response.put("fullReport", report.toString());

        return ResponseEntity.ok(response);
    }

    /**
     * QUICK VALIDATION CHECK
     * Basic validation for quick assessment
     */
    @GetMapping("/quick-check")
    public ResponseEntity<Map<String, Object>> quickValidationCheck() {
        Map<String, ValidationResult> allValidations = tickStore.validateAll();

        long totalSymbols = allValidations.size();
        long completeSymbols = allValidations.values().stream()
                .filter(ValidationResult::isComplete)
                .count();
        double avgCompleteness = allValidations.values().stream()
                .mapToDouble(ValidationResult::completenessPercentage)
                .average()
                .orElse(100.0);

        boolean passesBasicChecks = avgCompleteness >= 95.0 && completeSymbols == totalSymbols;

        return ResponseEntity.ok(Map.of(
                "validationType", "Quick Validation Check",
                "timestamp", Instant.now(),
                "totalSymbols", totalSymbols,
                "completeSymbols", completeSymbols,
                "incompleteSymbols", totalSymbols - completeSymbols,
                "averageCompleteness", avgCompleteness,
                "passesBasicChecks", passesBasicChecks,
                "recommendation", passesBasicChecks ?
                        "Model passes basic validation checks" :
                        "Model requires detailed validation - see comprehensive report"
        ));
    }

    /**
     * VALIDATION DASHBOARD
     * Summary view for monitoring/validation dashboard
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getValidationDashboard() {
        Map<String, ValidationResult> validations = tickStore.validateAll();

        // Calculate dashboard metrics
        long totalSymbols = validations.size();
        long completeSymbols = validations.values().stream()
                .filter(ValidationResult::isComplete)
                .count();
        double avgCompleteness = validations.values().stream()
                .mapToDouble(ValidationResult::completenessPercentage)
                .average()
                .orElse(100.0);

        // Identify symbols needing attention
        List<Map<String, Object>> symbolsNeedingAttention = validations.entrySet().stream()
                .filter(e -> !e.getValue().isComplete())
                .map(e -> Map.of(
                        "symbol", e.getKey(),
                        "completeness", e.getValue().completenessPercentage(),
                        "missingCount", e.getValue().missingCount(),
                        "gapCount", e.getValue().gaps().size()))
                .collect(Collectors.toList());

        // Get latest validation report
        ValidationReport latestReport = vvFramework.getLastReport();

        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("timestamp", Instant.now());
        dashboard.put("overview", Map.of(
                "totalSymbols", totalSymbols,
                "completeSymbols", completeSymbols,
                "incompleteSymbols", totalSymbols - completeSymbols,
                "averageCompleteness", avgCompleteness,
                "healthStatus", avgCompleteness >= 99.0 ? "HEALTHY" :
                               avgCompleteness >= 95.0 ? "ACCEPTABLE" : "NEEDS_ATTENTION"
        ));

        dashboard.put("symbols", validations);
        dashboard.put("symbolsNeedingAttention", symbolsNeedingAttention);

        if (latestReport != null) {
            dashboard.put("latestValidation", Map.of(
                    "reportId", latestReport.reportId(),
                    "credibilityScore", latestReport.credibilityScore(),
                    "isCredible", latestReport.isCredible(),
                    "grade", latestReport.getGrade(),
                    "isProductionReady", latestReport.isProductionReady()
            ));
        }

        return ResponseEntity.ok(dashboard);
    }
}

// Request DTOs
class HistoricalValidationRequest {
    private Map<String, List<Double>> historicalPatterns;
    private Map<String, Double> statisticalProperties;

    // Getters
    public Map<String, List<Double>> getHistoricalPatterns() { return historicalPatterns; }
    public Map<String, Double> getStatisticalProperties() { return statisticalProperties; }
}

class ExternalValidationRequest {
    private Map<String, RealSystemMetrics> realSystemMetrics;

    public Map<String, RealSystemMetrics> getRealSystemMetrics() { return realSystemMetrics; }
}

class SensitivityAnalysisRequest {
    private Map<String, List<Double>> inputVariations;

    public Map<String, List<Double>> getInputVariations() { return inputVariations; }
}

class StatisticalValidationRequest {
    private Map<String, List<Double>> expectedDistributions;

    public Map<String, List<Double>> getExpectedDistributions() { return expectedDistributions; }
}

class ComprehensiveValidationRequest {
    private Map<String, List<Double>> historicalPatterns;
    private Map<String, RealSystemMetrics> realSystemMetrics;
    private Map<String, List<Double>> sensitivityInputs;
    private Map<String, List<Double>> statisticalDistributions;

    public Map<String, List<Double>> getHistoricalPatterns() { return historicalPatterns; }
    public Map<String, RealSystemMetrics> getRealSystemMetrics() { return realSystemMetrics; }
    public Map<String, List<Double>> getSensitivityInputs() { return sensitivityInputs; }
    public Map<String, List<Double>> getStatisticalDistributions() { return statisticalDistributions; }
}