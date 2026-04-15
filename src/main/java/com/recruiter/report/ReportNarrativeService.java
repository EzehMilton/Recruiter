package com.recruiter.report;

import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.persistence.StoredEliminatedCandidate;

import java.util.List;

public interface ReportNarrativeService {

    ReportNarrative generate(ReportNarrativeRequest request);

    record ReportNarrativeRequest(
            String jobDescriptionText,
            String sector,
            String scoringMode,
            int totalSubmitted,
            int totalAnalysed,
            int totalShortlisted,
            int totalRejected,
            int totalEliminated,
            List<CandidateEvaluation> shortlistedCandidates,
            List<CandidateEvaluation> rejectedCandidates,
            List<StoredEliminatedCandidate> eliminatedCandidates
    ) {}
}
