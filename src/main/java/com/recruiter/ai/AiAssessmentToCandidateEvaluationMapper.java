package com.recruiter.ai;

import com.recruiter.config.RecruitmentProperties;
import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.CandidateProfile;
import com.recruiter.domain.CandidateScoreBreakdown;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiAssessmentToCandidateEvaluationMapper {

    private final RecruitmentProperties recruitmentProperties;

    public CandidateEvaluation map(CandidateProfile candidateProfile, AiFitAssessment assessment) {
        return map(candidateProfile, assessment, Sector.GENERIC);
    }

    public CandidateEvaluation map(CandidateProfile candidateProfile, AiFitAssessment assessment, Sector sector) {
        double finalScore;
        if (allDimensionsNull(assessment)) {
            finalScore = mapToLegacyScore(assessment.overallRecommendation(), assessment.confidence());
        } else {
            finalScore = calculateContinuousScore(assessment, sector);
        }

        double skillScore = round(finalScore * 0.50);
        double experienceScore = round(finalScore * 0.25);
        double keywordScore = round(finalScore - skillScore - experienceScore);

        return new CandidateEvaluation(
                candidateProfile,
                finalScore,
                new CandidateScoreBreakdown(skillScore, keywordScore, experienceScore),
                "ai",
                assessment.recruiterFacingExplanation(),
                false,
                assessment.confidence() != null ? assessment.confidence().name() : "",
                assessment.topStrengths() != null ? assessment.topStrengths() : java.util.List.of(),
                assessment.topGaps() != null ? assessment.topGaps() : java.util.List.of(),
                assessment.interviewProbeAreas() != null ? assessment.interviewProbeAreas() : java.util.List.of()
        );
    }

    private double calculateContinuousScore(AiFitAssessment assessment, Sector sector) {
        RecruitmentProperties.AiScoringWeights weights = recruitmentProperties.getResolvedAiScoring(sector);
        double weightedSum = levelValue(assessment.essentialFit()) * weights.essentialFitWeight()
                + levelValue(assessment.experienceFit()) * weights.experienceFitWeight()
                + levelValue(assessment.desirableFit()) * weights.desirableFitWeight()
                + levelValue(assessment.domainFit()) * weights.domainFitWeight()
                + levelValue(assessment.credentialsFit()) * weights.credentialsFitWeight();

        double baseScore = ((weightedSum - 1.0) / 3.0) * 100.0;
        double modifier = confidenceModifier(assessment.confidence());
        return round(clamp(baseScore * modifier));
    }

    private int levelValue(DimensionJudgement judgement) {
        if (judgement == null || judgement.level() == null) {
            return 2;
        }
        return switch (judgement.level()) {
            case STRONG -> 4;
            case PARTIAL -> 3;
            case WEAK -> 2;
            case NONE -> 1;
        };
    }

    private double confidenceModifier(ConfidenceLevel confidence) {
        if (confidence == null) {
            return 0.95;
        }
        return switch (confidence) {
            case HIGH -> 1.00;
            case MEDIUM -> 0.95;
            case LOW -> 0.85;
        };
    }

    private boolean allDimensionsNull(AiFitAssessment assessment) {
        return assessment.essentialFit() == null
                && assessment.desirableFit() == null
                && assessment.experienceFit() == null
                && assessment.domainFit() == null
                && assessment.credentialsFit() == null;
    }

    @Deprecated
    double mapToLegacyScore(MatchBand band, ConfidenceLevel confidence) {
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
        return clamp(raw);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
