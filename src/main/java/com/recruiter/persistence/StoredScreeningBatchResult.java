package com.recruiter.persistence;

import com.recruiter.domain.ScreeningResult;
import com.recruiter.support.BatchMetricsFormatter;

import java.math.BigDecimal;

public record StoredScreeningBatchResult(
        Long batchId,
        String createdAtDisplay,
        int shortlistCount,
        String scoringMode,
        int totalCvsReceived,
        int candidatesScored,
        Integer aiPromptTokens,
        Integer aiCompletionTokens,
        Integer aiTotalTokens,
        BigDecimal aiEstimatedCostUsd,
        Long processingTimeMs,
        ScreeningResult screeningResult
) {

    public boolean hasAiUsage() {
        return aiTotalTokens != null && aiTotalTokens > 0;
    }

    public String aiUsageDisplay() {
        return BatchMetricsFormatter.formatTokenUsage(aiTotalTokens, aiEstimatedCostUsd);
    }

    public String processingTimeDisplay() {
        return BatchMetricsFormatter.formatDuration(processingTimeMs);
    }
}
