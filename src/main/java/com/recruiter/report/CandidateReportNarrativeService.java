package com.recruiter.report;

import com.recruiter.ai.AiResult;

public interface CandidateReportNarrativeService {

    AiResult<CandidateReportNarrative> generate(CandidateReportNarrativeRequest request);

    record CandidateReportNarrativeRequest(
            String candidateName,
            String cvText,
            String jobDescriptionText,
            String sector,
            String scoringMode,
            double score
    ) {}
}
