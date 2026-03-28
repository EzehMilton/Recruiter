package com.recruiter.domain;

import java.util.List;
import java.util.Objects;

public record CandidateEvaluation(
        CandidateProfile candidateProfile,
        double score,
        CandidateScoreBreakdown scoreBreakdown,
        String scoringPath,
        String summary,
        boolean shortlisted,
        String aiConfidence,
        List<String> aiTopStrengths,
        List<String> aiTopGaps,
        List<String> aiInterviewProbeAreas
) {

    public CandidateEvaluation {
        candidateProfile = Objects.requireNonNull(candidateProfile, "candidateProfile must not be null");
        scoreBreakdown = Objects.requireNonNullElse(scoreBreakdown, CandidateScoreBreakdown.empty());
        scoringPath = Objects.requireNonNullElse(scoringPath, "heuristic");
        summary = Objects.requireNonNullElse(summary, "").trim();
        aiConfidence = aiConfidence != null ? aiConfidence : "";
        aiTopStrengths = aiTopStrengths != null ? List.copyOf(aiTopStrengths) : List.of();
        aiTopGaps = aiTopGaps != null ? List.copyOf(aiTopGaps) : List.of();
        aiInterviewProbeAreas = aiInterviewProbeAreas != null ? List.copyOf(aiInterviewProbeAreas) : List.of();
    }

    public CandidateEvaluation(CandidateProfile candidateProfile,
                               double score,
                               String summary,
                               boolean shortlisted) {
        this(candidateProfile, score, CandidateScoreBreakdown.empty(), "heuristic", summary, shortlisted,
                "", List.of(), List.of(), List.of());
    }

    public boolean isAiScored() {
        return "ai".equals(scoringPath);
    }
}
