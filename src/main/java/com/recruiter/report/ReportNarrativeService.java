package com.recruiter.report;

import com.recruiter.ai.AiResult;
import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.ScreeningPackage;
import com.recruiter.persistence.StoredEliminatedCandidate;

import java.util.List;

public interface ReportNarrativeService {

    AiResult<ReportNarrative> generate(ReportNarrativeRequest request);

    record ReportNarrativeRequest(
            String jobDescriptionText,
            String sector,
            String scoringMode,
            ScreeningPackage screeningPackage,
            int totalSubmitted,
            int totalAnalysed,
            int totalShortlisted,
            int totalRejected,
            int totalEliminated,
            List<CandidateEvaluation> shortlistedCandidates,
            List<CandidateEvaluation> rejectedCandidates,
            List<StoredEliminatedCandidate> eliminatedCandidates
    ) {
        public ReportNarrativeRequest(String jobDescriptionText,
                                      String sector,
                                      String scoringMode,
                                      int totalSubmitted,
                                      int totalAnalysed,
                                      int totalShortlisted,
                                      int totalRejected,
                                      int totalEliminated,
                                      List<CandidateEvaluation> shortlistedCandidates,
                                      List<CandidateEvaluation> rejectedCandidates,
                                      List<StoredEliminatedCandidate> eliminatedCandidates) {
            this(jobDescriptionText, sector, scoringMode, ScreeningPackage.QUICK_SCREEN, totalSubmitted,
                    totalAnalysed, totalShortlisted, totalRejected, totalEliminated,
                    shortlistedCandidates, rejectedCandidates, eliminatedCandidates);
        }
    }
}
