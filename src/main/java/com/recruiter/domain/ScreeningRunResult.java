package com.recruiter.domain;

import com.recruiter.ai.Sector;
import com.recruiter.ai.TokenUsage;
import com.recruiter.support.BatchMetricsFormatter;

import java.util.Objects;

public record ScreeningRunResult(
        Long batchId,
        int shortlistCount,
        ScoringMode effectiveScoringMode,
        Sector sector,
        int totalCvsReceived,
        int exactDuplicateCvsRemoved,
        int nearDuplicateCvsRemoved,
        int duplicateCvsRemoved,
        int candidatesScored,
        TokenUsage aiTokenUsage,
        Double aiEstimatedCostUsd,
        Long processingTimeMs,
        ScreeningResult screeningResult
) {

    public ScreeningRunResult {
        effectiveScoringMode = Objects.requireNonNull(effectiveScoringMode, "effectiveScoringMode must not be null");
        sector = Objects.requireNonNullElse(sector, Sector.GENERIC);
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

    public String duplicateSummary() {
        if (duplicateCvsRemoved <= 0) {
            return null;
        }
        if (nearDuplicateCvsRemoved > 0) {
            return exactDuplicateCvsRemoved + " exact duplicate CV(s) and "
                    + nearDuplicateCvsRemoved + " near-duplicate CV(s) were detected and removed before scoring.";
        }
        return duplicateCvsRemoved + " duplicate CV(s) were detected and removed before scoring.";
    }

    public String sectorDisplay() {
        return sector.getLabel();
    }
}
