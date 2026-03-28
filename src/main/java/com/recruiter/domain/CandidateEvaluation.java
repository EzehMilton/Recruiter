package com.recruiter.domain;

import com.recruiter.screening.CandidateScoreDetails;

import java.util.Objects;

public record CandidateEvaluation(
        CandidateProfile candidateProfile,
        double score,
        CandidateScoreDetails scoreDetails,
        String summary,
        boolean shortlisted
) {

    public CandidateEvaluation {
        candidateProfile = Objects.requireNonNull(candidateProfile, "candidateProfile must not be null");
        summary = Objects.requireNonNullElse(summary, "").trim();
    }
}
