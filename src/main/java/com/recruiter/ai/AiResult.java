package com.recruiter.ai;

import java.util.Objects;

public record AiResult<T>(
        T result,
        TokenUsage tokenUsage
) {

    public AiResult {
        tokenUsage = Objects.requireNonNullElse(tokenUsage, TokenUsage.ZERO);
    }
}
