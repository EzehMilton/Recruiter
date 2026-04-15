package com.recruiter.report;

public interface CandidateReportNarrativeService {

    CandidateReportNarrative generate(CandidateReportNarrativeRequest request);

    record CandidateReportNarrativeRequest(
            String candidateName,
            String cvText,
            String jobDescriptionText,
            String sector,
            String scoringMode,
            double score
    ) {}
}
