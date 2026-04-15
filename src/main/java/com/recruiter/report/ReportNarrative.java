package com.recruiter.report;

public record ReportNarrative(
        String executiveSummary,
        String methodologyText,
        String nextSteps
) {

    public static ReportNarrative empty() {
        return new ReportNarrative("", "", "");
    }
}
