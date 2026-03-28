package com.recruiter.ai;

import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.CandidateProfile;
import com.recruiter.domain.CandidateScoreBreakdown;
import org.springframework.stereotype.Service;

@Service
public class AiAssessmentToCandidateEvaluationMapper {

    public CandidateEvaluation map(CandidateProfile candidateProfile, AiFitAssessment assessment) {
        double internalScore = mapToInternalScore(assessment.overallRecommendation(), assessment.confidence());
        return new CandidateEvaluation(
                candidateProfile,
                internalScore,
                new CandidateScoreBreakdown(internalScore, 0.0, 0.0),
                "ai",
                assessment.recruiterFacingExplanation(),
                false,
                assessment.confidence() != null ? assessment.confidence().name() : "",
                assessment.topStrengths() != null ? assessment.topStrengths() : java.util.List.of(),
                assessment.topGaps() != null ? assessment.topGaps() : java.util.List.of(),
                assessment.interviewProbeAreas() != null ? assessment.interviewProbeAreas() : java.util.List.of()
        );
    }

    double mapToInternalScore(MatchBand band, ConfidenceLevel confidence) {
        double raw = switch (band) {
            case STRONG_MATCH -> switch (confidence) {
                case HIGH -> 90;
                case MEDIUM -> 85;
                case LOW -> 80;
            };
            case POSSIBLE_MATCH -> switch (confidence) {
                case HIGH -> 72;
                case MEDIUM -> 68;
                case LOW -> 62;
            };
            case WEAK_MATCH -> switch (confidence) {
                case HIGH -> 48;
                case MEDIUM -> 42;
                case LOW -> 35;
            };
            case NOT_RECOMMENDED -> switch (confidence) {
                case HIGH -> 20;
                case MEDIUM -> 15;
                case LOW -> 10;
            };
        };
        return Math.max(0.0, Math.min(100.0, raw));
    }
}
