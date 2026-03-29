package com.recruiter.domain;

import com.recruiter.ai.TokenUsage;
import com.recruiter.support.BatchMetricsFormatter;

import java.util.Objects;

public record ScreeningRunResult(
        Long batchId,
        int shortlistCount,
        ScoringMode effectiveScoringMode,
        int totalCvsReceived,
        int duplicateCvsRemoved,
        int candidatesScored,
        TokenUsage aiTokenUsage,
        Double aiEstimatedCostUsd,
        Long processingTimeMs,
        ScreeningResult screeningResult
) {

    public ScreeningRunResult {
        effectiveScoringMode = Objects.requireNonNull(effectiveScoringMode, "effectiveScoringMode must not be null");
        aiTokenUsage = Objects.requireNonNullElse(aiTokenUsage, TokenUsage.ZERO);
        screeningResult = Objects.requireNonNull(screeningResult, "screeningResult must not be null");
    }

    public boolean wasReduced() {
        return (totalCvsReceived - duplicateCvsRemoved) > candidatesScored;
    }

    public boolean hasAiUsage() {
        return aiTokenUsage.totalTokens() > 0;
    }

    public String aiUsageDisplay() {
        return BatchMetricsFormatter.formatTokenUsage(aiTokenUsage, aiEstimatedCostUsd);
    }
}
