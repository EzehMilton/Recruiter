package com.recruiter.support;

import com.recruiter.ai.TokenUsage;

import java.math.BigDecimal;
import java.util.Locale;

public final class BatchMetricsFormatter {

    private BatchMetricsFormatter() {
    }

    public static String formatTokenUsage(TokenUsage tokenUsage, Double estimatedCostUsd) {
        if (tokenUsage == null || tokenUsage.totalTokens() <= 0) {
            return null;
        }
        return String.format(Locale.US, "%,d tokens (%s)",
                tokenUsage.totalTokens(), formatApproxCost(estimatedCostUsd));
    }

    public static String formatTokenUsage(Integer totalTokens, BigDecimal estimatedCostUsd) {
        if (totalTokens == null || totalTokens <= 0) {
            return null;
        }
        return String.format(Locale.US, "%,d tokens (%s)",
                totalTokens, formatApproxCost(estimatedCostUsd != null ? estimatedCostUsd.doubleValue() : null));
    }

    public static String formatApproxCost(Double estimatedCostUsd) {
        double safeCost = estimatedCostUsd != null ? estimatedCostUsd : 0.0;
        if (safeCost >= 0.01) {
            return String.format(Locale.US, "~$%.2f", safeCost);
        }
        if (safeCost >= 0.001) {
            return String.format(Locale.US, "~$%.3f", safeCost);
        }
        return String.format(Locale.US, "~$%.4f", safeCost);
    }

    public static String formatDuration(Long processingTimeMs) {
        if (processingTimeMs == null || processingTimeMs < 0) {
            return null;
        }
        if (processingTimeMs < 1000) {
            return processingTimeMs + "ms";
        }
        return String.format(Locale.US, "%.1fs", processingTimeMs / 1000.0);
    }
}
