package com.recruiter.domain;

import java.util.List;
import java.util.Objects;

public record ScreeningResult(
        JobDescriptionProfile jobDescriptionProfile,
        List<CandidateEvaluation> candidateEvaluations
) {

    public ScreeningResult {
        jobDescriptionProfile = Objects.requireNonNull(jobDescriptionProfile, "jobDescriptionProfile must not be null");
        candidateEvaluations = List.copyOf(Objects.requireNonNullElse(candidateEvaluations, List.of()));
    }

    public List<CandidateEvaluation> shortlistedCandidates() {
        return candidateEvaluations.stream()
                .filter(CandidateEvaluation::shortlisted)
                .toList();
    }
}
