package com.recruiter.report;

import com.recruiter.ai.AiResult;
import com.recruiter.domain.ScreeningPackage;

public interface CandidateReportNarrativeService {

    AiResult<CandidateReportNarrative> generate(CandidateReportNarrativeRequest request);

    record CandidateReportNarrativeRequest(
            String candidateName,
            String cvText,
            String jobDescriptionText,
            String sector,
            String scoringMode,
            ScreeningPackage screeningPackage,
            double score
    ) {
        public CandidateReportNarrativeRequest(String candidateName,
                                               String cvText,
                                               String jobDescriptionText,
                                               String sector,
                                               String scoringMode,
                                               double score) {
            this(candidateName, cvText, jobDescriptionText, sector, scoringMode,
                    ScreeningPackage.QUICK_SCREEN, score);
        }
    }
}
