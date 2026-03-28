package com.recruiter.orchestration.embabel;

import java.util.Objects;

public record CandidateSummary(String value) {

    public CandidateSummary {
        value = Objects.requireNonNullElse(value, "").trim();
    }
}
