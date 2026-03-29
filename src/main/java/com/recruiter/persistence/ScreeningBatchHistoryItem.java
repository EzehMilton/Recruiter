package com.recruiter.persistence;

import com.recruiter.support.BatchMetricsFormatter;

import java.math.BigDecimal;

public record ScreeningBatchHistoryItem(
        Long batchId,
        String createdAtDisplay,
        int candidateCount,
        int shortlistCount,
        String scoringMode,
        int totalCvsReceived,
        int candidatesScored,
        Integer aiTotalTokens,
        BigDecimal aiEstimatedCostUsd,
        Long processingTimeMs
) {

    public String aiUsageDisplay() {
        return BatchMetricsFormatter.formatTokenUsage(aiTotalTokens, aiEstimatedCostUsd);
    }

    public String processingTimeDisplay() {
        return BatchMetricsFormatter.formatDuration(processingTimeMs);
    }
}
