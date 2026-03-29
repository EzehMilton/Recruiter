package com.recruiter.ai;

public record TokenUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens
) {

    public static final TokenUsage ZERO = new TokenUsage(0, 0, 0);

    public TokenUsage add(TokenUsage other) {
        TokenUsage safeOther = other != null ? other : ZERO;
        return new TokenUsage(
                this.promptTokens + safeOther.promptTokens,
                this.completionTokens + safeOther.completionTokens,
                this.totalTokens + safeOther.totalTokens
        );
    }

    public double estimatedCostUsd(double promptPricePerMillionTokens, double completionPricePerMillionTokens) {
        return (promptTokens * promptPricePerMillionTokens / 1_000_000.0)
                + (completionTokens * completionPricePerMillionTokens / 1_000_000.0);
    }
}
