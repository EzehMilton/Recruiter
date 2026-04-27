package com.recruiter.persistence;

import com.recruiter.support.BatchMetricsFormatter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

public record AnalyticsSummary(
        long totalBatches,
        long aiBatches,
        BigDecimal totalCostUsd,
        BigDecimal avgCostPerBatch,
        BigDecimal avgCostPerSuccessfulBatch,
        Long avgLatencyMs,
        Long p95LatencyMs,
        Long p99LatencyMs,
        Long minLatencyMs,
        Long maxLatencyMs,
        List<GroupMetrics> metricsByPackage,
        List<GroupMetrics> metricsByScoringMode
) {

    public record GroupMetrics(
            String label,
            Double avgCostUsd,
            Double avgLatencyMs,
            long batchCount
    ) {
        public String avgCostDisplay() {
            return avgCostUsd != null && avgCostUsd > 0
                    ? BatchMetricsFormatter.formatApproxCost(avgCostUsd)
                    : "—";
        }

        public String avgLatencyDisplay() {
            return avgLatencyMs != null
                    ? BatchMetricsFormatter.formatDuration(avgLatencyMs.longValue())
                    : "—";
        }
    }

    public boolean hasData() {
        return totalBatches > 0;
    }

    public boolean hasLatencyData() {
        return avgLatencyMs != null;
    }

    public boolean hasCostData() {
        return aiBatches > 0;
    }

    public String totalCostDisplay() {
        return totalCostUsd != null
                ? BatchMetricsFormatter.formatApproxCost(totalCostUsd.doubleValue())
                : "—";
    }

    public String avgCostPerBatchDisplay() {
        return avgCostPerBatch != null && avgCostPerBatch.compareTo(BigDecimal.ZERO) > 0
                ? BatchMetricsFormatter.formatApproxCost(avgCostPerBatch.doubleValue())
                : "—";
    }

    public String avgCostPerSuccessfulDisplay() {
        return avgCostPerSuccessfulBatch != null && avgCostPerSuccessfulBatch.compareTo(BigDecimal.ZERO) > 0
                ? BatchMetricsFormatter.formatApproxCost(avgCostPerSuccessfulBatch.doubleValue())
                : "—";
    }

    public String avgLatencyDisplay() {
        return BatchMetricsFormatter.formatDuration(avgLatencyMs);
    }

    public String p95LatencyDisplay() {
        return BatchMetricsFormatter.formatDuration(p95LatencyMs);
    }

    public String p99LatencyDisplay() {
        return BatchMetricsFormatter.formatDuration(p99LatencyMs);
    }

    public String minLatencyDisplay() {
        return BatchMetricsFormatter.formatDuration(minLatencyMs);
    }

    public String maxLatencyDisplay() {
        return BatchMetricsFormatter.formatDuration(maxLatencyMs);
    }

    public String projectedCostDisplay(int volume) {
        if (avgCostPerBatch == null || avgCostPerBatch.compareTo(BigDecimal.ZERO) == 0) {
            return "—";
        }
        double projected = avgCostPerBatch.doubleValue() * volume;
        if (projected >= 1.0) {
            return String.format(Locale.US, "~$%.2f", projected);
        }
        if (projected >= 0.01) {
            return String.format(Locale.US, "~$%.3f", projected);
        }
        return String.format(Locale.US, "~$%.4f", projected);
    }
}
