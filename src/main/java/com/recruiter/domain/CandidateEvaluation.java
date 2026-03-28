package com.recruiter.domain;

import java.util.Objects;

public record CandidateEvaluation(
        CandidateProfile candidateProfile,
        double score,
        CandidateScoreBreakdown scoreBreakdown,
        String summary,
        boolean shortlisted
) {

    public CandidateEvaluation {
        candidateProfile = Objects.requireNonNull(candidateProfile, "candidateProfile must not be null");
        scoreBreakdown = Objects.requireNonNullElse(scoreBreakdown, CandidateScoreBreakdown.empty());
        summary = Objects.requireNonNullElse(summary, "").trim();
    }

    public CandidateEvaluation(CandidateProfile candidateProfile,
                               double score,
                               String summary,
                               boolean shortlisted) {
        this(candidateProfile, score, CandidateScoreBreakdown.empty(), summary, shortlisted);
    }
}
