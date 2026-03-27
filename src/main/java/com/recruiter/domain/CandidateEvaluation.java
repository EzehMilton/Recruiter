package com.recruiter.domain;

import java.util.Objects;

public record CandidateEvaluation(
        CandidateProfile candidateProfile,
        double score,
        String summary,
        boolean shortlisted
) {

    public CandidateEvaluation {
        candidateProfile = Objects.requireNonNull(candidateProfile, "candidateProfile must not be null");
        summary = Objects.requireNonNullElse(summary, "").trim();
    }
}
