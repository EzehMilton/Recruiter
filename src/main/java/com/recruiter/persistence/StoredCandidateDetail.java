package com.recruiter.persistence;

import com.recruiter.domain.CandidateEvaluation;

public record StoredCandidateDetail(
        Long batchId,
        String batchCreatedAtDisplay,
        CandidateEvaluation evaluation,
        int rank,
        int totalCandidates
) {
}
