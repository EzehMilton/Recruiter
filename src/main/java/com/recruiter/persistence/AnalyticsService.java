package com.recruiter.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ScreeningBatchRepository screeningBatchRepository;

    @Transactional(readOnly = true)
    public AnalyticsSummary load() {
        long totalBatches = screeningBatchRepository.count();

        Object[] aiUsage = screeningBatchRepository.findTotalAiUsage();
        long aiBatches = aiUsage != null && aiUsage.length >= 3 && aiUsage[2] instanceof Number n
                ? n.longValue() : 0L;
        BigDecimal totalCost = aiUsage != null && aiUsage.length >= 2 && aiUsage[1] instanceof BigDecimal bd
                ? bd : BigDecimal.ZERO;

        BigDecimal avgCostPerBatch = aiBatches > 0 && totalCost.compareTo(BigDecimal.ZERO) > 0
                ? totalCost.divide(BigDecimal.valueOf(aiBatches), 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal avgCostPerSuccessful = screeningBatchRepository.findAvgCostPerSuccessfulBatch();

        List<Long> latencies = screeningBatchRepository.findAllProcessingTimesAsc();

        Long avgLatencyMs = latencies.isEmpty() ? null
                : (long) latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        Long p95LatencyMs = latencies.isEmpty() ? null : percentile(latencies, 95);
        Long p99LatencyMs = latencies.isEmpty() ? null : percentile(latencies, 99);
        Long minLatencyMs = latencies.isEmpty() ? null : latencies.getFirst();
        Long maxLatencyMs = latencies.isEmpty() ? null : latencies.getLast();

        List<AnalyticsSummary.GroupMetrics> byPackage = screeningBatchRepository.findMetricsByPackage()
                .stream()
                .map(row -> toGroupMetrics(row, formatPackageLabel((String) row[0])))
                .toList();

        List<AnalyticsSummary.GroupMetrics> byMode = screeningBatchRepository.findMetricsByScoringMode()
                .stream()
                .map(row -> toGroupMetrics(row, formatModeLabel((String) row[0])))
                .toList();

        return new AnalyticsSummary(
                totalBatches, aiBatches,
                totalCost, avgCostPerBatch, avgCostPerSuccessful,
                avgLatencyMs, p95LatencyMs, p99LatencyMs, minLatencyMs, maxLatencyMs,
                byPackage, byMode
        );
    }

    private static Long percentile(List<Long> sorted, int pct) {
        int index = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static AnalyticsSummary.GroupMetrics toGroupMetrics(Object[] row, String label) {
        Double avgCost = row[1] instanceof Number n ? n.doubleValue() : null;
        Double avgLatency = row[2] instanceof Number n ? n.doubleValue() : null;
        long count = row[3] instanceof Number n ? n.longValue() : 0L;
        return new AnalyticsSummary.GroupMetrics(label, avgCost, avgLatency, count);
    }

    private static String formatPackageLabel(String raw) {
        if (raw == null) return "Unknown";
        return switch (raw.toUpperCase()) {
            case "QUICK_SCREEN" -> "Quick Screen";
            case "STANDARD_SCREEN" -> "Standard Screen";
            case "PREMIUM_PACK" -> "Premium Pack";
            default -> raw;
        };
    }

    private static String formatModeLabel(String raw) {
        if (raw == null) return "Unknown";
        return switch (raw.toLowerCase()) {
            case "ai" -> "AI";
            case "heuristic" -> "Heuristic";
            case "ai_with_fallbacks" -> "AI (with fallbacks)";
            default -> raw;
        };
    }
}
