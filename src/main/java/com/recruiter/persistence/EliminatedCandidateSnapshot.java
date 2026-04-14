package com.recruiter.persistence;

import java.util.List;

public record EliminatedCandidateSnapshot(
        String candidateName,
        String candidateFilename,
        double preFilterScore,
        List<String> matchedSkills,
        String scoreLabel,
        String eliminationReason
) {
}
