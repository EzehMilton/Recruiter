package com.recruiter.persistence;

import com.recruiter.domain.ScreeningResult;
import com.recruiter.domain.ScreeningPackage;
import com.recruiter.support.BatchMetricsFormatter;

import java.math.BigDecimal;

public record StoredScreeningBatchResult(
        Long batchId,
        String createdAtDisplay,
        int shortlistCount,
        String scoringMode,
        ScreeningPackage screeningPackage,
        String sector,
        int totalCvsReceived,
        int candidatesScored,
        Integer aiPromptTokens,
        Integer aiCompletionTokens,
        Integer aiTotalTokens,
        BigDecimal aiEstimatedCostUsd,
        Long processingTimeMs,
        ScreeningResult screeningResult
) {

    public StoredScreeningBatchResult(Long batchId,
                                      String createdAtDisplay,
                                      int shortlistCount,
                                      String scoringMode,
                                      String sector,
                                      int totalCvsReceived,
                                      int candidatesScored,
                                      Integer aiPromptTokens,
                                      Integer aiCompletionTokens,
                                      Integer aiTotalTokens,
                                      BigDecimal aiEstimatedCostUsd,
                                      Long processingTimeMs,
                                      ScreeningResult screeningResult) {
        this(batchId, createdAtDisplay, shortlistCount, scoringMode, ScreeningPackage.QUICK_SCREEN, sector,
                totalCvsReceived, candidatesScored, aiPromptTokens, aiCompletionTokens, aiTotalTokens,
                aiEstimatedCostUsd, processingTimeMs, screeningResult);
    }

    public boolean hasAiUsage() {
        return aiTotalTokens != null && aiTotalTokens > 0;
    }

    public String aiUsageDisplay() {
        return BatchMetricsFormatter.formatTokenUsage(aiTotalTokens, aiEstimatedCostUsd);
    }

    public String processingTimeDisplay() {
        return BatchMetricsFormatter.formatDuration(processingTimeMs);
    }

    public String sectorDisplay() {
        return com.recruiter.ai.Sector.fromString(sector).getLabel();
    }
}
