package com.recruiter.ai;

import java.util.List;

public record AiFitAssessment(
        MatchBand overallRecommendation,
        ConfidenceLevel confidence,
        DimensionJudgement essentialFit,
        DimensionJudgement desirableFit,
        DimensionJudgement experienceFit,
        DimensionJudgement domainFit,
        DimensionJudgement credentialsFit,
        List<String> topStrengths,
        List<String> topGaps,
        List<String> interviewProbeAreas,
        String recruiterFacingExplanation
) {
}
