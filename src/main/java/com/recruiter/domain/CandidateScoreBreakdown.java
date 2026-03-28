package com.recruiter.domain;

public record CandidateScoreBreakdown(
        double skillScore,
        double keywordScore,
        double experienceScore
) {

    public CandidateScoreBreakdown {
        skillScore = round(skillScore);
        keywordScore = round(keywordScore);
        experienceScore = round(experienceScore);
    }

    public static CandidateScoreBreakdown empty() {
        return new CandidateScoreBreakdown(0.0, 0.0, 0.0);
    }

    public double totalWeightedScore() {
        return round(skillScore + keywordScore + experienceScore);
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
