package com.recruiter.domain;

import java.util.Objects;

public record ScreeningRunResult(
        Long batchId,
        int shortlistCount,
        ScreeningResult screeningResult
) {

    public ScreeningRunResult {
        screeningResult = Objects.requireNonNull(screeningResult, "screeningResult must not be null");
    }
}
