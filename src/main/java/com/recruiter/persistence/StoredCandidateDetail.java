package com.recruiter.persistence;

import com.recruiter.domain.CandidateEvaluation;

public record StoredCandidateDetail(
        Long batchId,
        String createdAtDisplay,
        int shortlistCount,
        int rankPosition,
        CandidateEvaluation candidateEvaluation
) {
}
