package com.recruiter.persistence;

import com.recruiter.support.BatchMetricsFormatter;

import java.math.BigDecimal;

public record AiUsageSummary(
        long totalTokens,
        BigDecimal totalEstimatedCostUsd,
        long batchCount
) {

    public boolean hasUsage() {
        return totalTokens > 0;
    }

    public String displayText() {
        if (!hasUsage()) {
            return null;
        }
        return "Total AI usage: " + String.format("%,d", totalTokens)
                + " tokens across " + batchCount + " batches ("
                + BatchMetricsFormatter.formatApproxCost(totalEstimatedCostUsd != null ? totalEstimatedCostUsd.doubleValue() : null)
                + ")";
    }
}
