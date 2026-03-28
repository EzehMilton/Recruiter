package com.recruiter.domain;

import java.util.Objects;

public record ScreeningRunResult(
        Long batchId,
        int shortlistCount,
        ScoringMode effectiveScoringMode,
        ScreeningResult screeningResult
) {

    public ScreeningRunResult {
        effectiveScoringMode = Objects.requireNonNull(effectiveScoringMode, "effectiveScoringMode must not be null");
        screeningResult = Objects.requireNonNull(screeningResult, "screeningResult must not be null");
    }
}
