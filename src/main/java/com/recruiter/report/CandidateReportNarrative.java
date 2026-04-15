package com.recruiter.report;

import java.util.List;

public record CandidateReportNarrative(
        String candidateSummary,
        List<String> strengths,
        List<String> weaknesses,
        String roleFitSummary,
        List<InterviewQuestion> interviewQuestions
) {
    public CandidateReportNarrative {
        candidateSummary = candidateSummary != null ? candidateSummary : "";
        strengths = strengths != null ? List.copyOf(strengths) : List.of();
        weaknesses = weaknesses != null ? List.copyOf(weaknesses) : List.of();
        roleFitSummary = roleFitSummary != null ? roleFitSummary : "";
        interviewQuestions = interviewQuestions != null ? List.copyOf(interviewQuestions) : List.of();
    }

    public static CandidateReportNarrative empty() {
        return new CandidateReportNarrative("", List.of(), List.of(), "", List.of());
    }
}
