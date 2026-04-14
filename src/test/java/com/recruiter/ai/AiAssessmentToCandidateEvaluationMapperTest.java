package com.recruiter.ai;

import com.recruiter.config.RecruitmentProperties;
import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.CandidateProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@ExtendWith(OutputCaptureExtension.class)
class AiAssessmentToCandidateEvaluationMapperTest {

    private final RecruitmentProperties recruitmentProperties = defaultProperties();
    private final AiAssessmentToCandidateEvaluationMapper mapper = new AiAssessmentToCandidateEvaluationMapper(recruitmentProperties);

    @ParameterizedTest
    @CsvSource({
            "STRONG_MATCH, HIGH, 90",
            "STRONG_MATCH, MEDIUM, 85",
            "STRONG_MATCH, LOW, 80",
            "POSSIBLE_MATCH, HIGH, 72",
            "POSSIBLE_MATCH, MEDIUM, 68",
            "POSSIBLE_MATCH, LOW, 62",
            "WEAK_MATCH, HIGH, 48",
            "WEAK_MATCH, MEDIUM, 42",
            "WEAK_MATCH, LOW, 35",
            "NOT_RECOMMENDED, HIGH, 20",
            "NOT_RECOMMENDED, MEDIUM, 15",
            "NOT_RECOMMENDED, LOW, 10"
    })
    void legacyScoreMapsMatchBandAndConfidenceToExpectedScore(MatchBand band, ConfidenceLevel confidence, double expectedScore) {
        assertThat(mapper.mapToLegacyScore(band, confidence)).isEqualTo(expectedScore);
    }

    @Test
    void mapsBuildsCandidateEvaluationWithContinuousScore() {
        CandidateProfile profile = new CandidateProfile("Alice", "alice.pdf", "", List.of(), null);
        AiFitAssessment assessment = new AiFitAssessment(
                MatchBand.POSSIBLE_MATCH,
                ConfidenceLevel.HIGH,
                new DimensionJudgement(JudgementLevel.STRONG, "Good skills"),
                new DimensionJudgement(JudgementLevel.PARTIAL, "Some desirable"),
                new DimensionJudgement(JudgementLevel.STRONG, "Enough experience"),
                new DimensionJudgement(JudgementLevel.WEAK, "Different domain"),
                new DimensionJudgement(JudgementLevel.NONE, "No certs"),
                List.of("Java", "Spring"),
                List.of("No AWS"),
                List.of("Ask about cloud"),
                "Solid Java developer with good experience but missing AWS."
        );

        CandidateEvaluation evaluation = mapper.map(profile, assessment);

        // weightedSum = (4*0.35)+(4*0.25)+(3*0.15)+(2*0.15)+(1*0.10) = 3.25
        // baseScore = ((3.25-1.0)/3.0)*100 = 75.0, HIGH -> *1.00 = 75.0
        assertThat(evaluation.score()).isEqualTo(75.0);
        assertThat(evaluation.scoreBreakdown().skillScore()).isEqualTo(37.5);
        assertThat(evaluation.scoreBreakdown().experienceScore()).isEqualTo(18.8);
        assertThat(evaluation.summary()).isEqualTo("Solid Java developer with good experience but missing AWS.");
        assertThat(evaluation.shortlisted()).isFalse();
        assertThat(evaluation.scoringPath()).isEqualTo("ai");
        assertThat(evaluation.aiConfidence()).isEqualTo("HIGH");
        assertThat(evaluation.aiTopStrengths()).containsExactly("Java", "Spring");
        assertThat(evaluation.aiTopGaps()).containsExactly("No AWS");
        assertThat(evaluation.aiInterviewProbeAreas()).containsExactly("Ask about cloud");
    }

    @Test
    void allStrongWithHighConfidenceScores100() {
        CandidateEvaluation eval = mapWithUniformDimensions(JudgementLevel.STRONG, ConfidenceLevel.HIGH);
        assertThat(eval.score()).isEqualTo(100.0);
    }

    @Test
    void allNoneWithHighConfidenceScores0() {
        CandidateEvaluation eval = mapWithUniformDimensions(JudgementLevel.NONE, ConfidenceLevel.HIGH);
        assertThat(eval.score()).isEqualTo(0.0);
    }

    @Test
    void allPartialWithMediumConfidenceScores63Point3() {
        // weightedSum = 3.0, baseScore = ((3.0-1.0)/3.0)*100 = 66.67, *0.95 = 63.3
        CandidateEvaluation eval = mapWithUniformDimensions(JudgementLevel.PARTIAL, ConfidenceLevel.MEDIUM);
        assertThat(eval.score()).isEqualTo(63.3);
    }

    @Test
    void allWeakWithLowConfidenceScores28Point3() {
        // weightedSum = 2.0, baseScore = ((2.0-1.0)/3.0)*100 = 33.33, *0.85 = 28.3
        CandidateEvaluation eval = mapWithUniformDimensions(JudgementLevel.WEAK, ConfidenceLevel.LOW);
        assertThat(eval.score()).isEqualTo(28.3);
    }

    @Test
    void mixedDimensionsWithMediumConfidenceScores60Point2() {
        // STRONG essential, WEAK experience, PARTIAL desirable, PARTIAL domain, NONE credentials
        // weightedSum = (4*0.35)+(2*0.25)+(3*0.15)+(3*0.15)+(1*0.10) = 2.9
        // baseScore = ((2.9-1.0)/3.0)*100 = 63.33, *0.95 = 60.2
        CandidateProfile profile = new CandidateProfile("Test", "test.pdf", "", List.of(), null);
        AiFitAssessment assessment = new AiFitAssessment(
                MatchBand.POSSIBLE_MATCH, ConfidenceLevel.MEDIUM,
                new DimensionJudgement(JudgementLevel.STRONG, ""),
                new DimensionJudgement(JudgementLevel.PARTIAL, ""),
                new DimensionJudgement(JudgementLevel.WEAK, ""),
                new DimensionJudgement(JudgementLevel.PARTIAL, ""),
                new DimensionJudgement(JudgementLevel.NONE, ""),
                List.of(), List.of(), List.of(), ""
        );

        CandidateEvaluation eval = mapper.map(profile, assessment);
        assertThat(eval.score()).isEqualTo(60.2);
    }

    @Test
    void customAiWeightsChangeScoreProportionally() {
        RecruitmentProperties properties = defaultProperties();
        properties.getScoring().getAi().setEssentialFitWeight(0.20);
        properties.getScoring().getAi().setExperienceFitWeight(0.20);
        properties.getScoring().getAi().setDesirableFitWeight(0.20);
        properties.getScoring().getAi().setDomainFitWeight(0.20);
        properties.getScoring().getAi().setCredentialsFitWeight(0.20);
        properties.afterPropertiesSet();
        AiAssessmentToCandidateEvaluationMapper customMapper = new AiAssessmentToCandidateEvaluationMapper(properties);

        CandidateProfile profile = new CandidateProfile("Alice", "alice.pdf", "", List.of(), null);
        AiFitAssessment assessment = new AiFitAssessment(
                MatchBand.POSSIBLE_MATCH,
                ConfidenceLevel.HIGH,
                new DimensionJudgement(JudgementLevel.STRONG, "Good skills"),
                new DimensionJudgement(JudgementLevel.PARTIAL, "Some desirable"),
                new DimensionJudgement(JudgementLevel.STRONG, "Enough experience"),
                new DimensionJudgement(JudgementLevel.WEAK, "Different domain"),
                new DimensionJudgement(JudgementLevel.NONE, "No certs"),
                List.of(), List.of(), List.of(), ""
        );

        CandidateEvaluation evaluation = customMapper.map(profile, assessment);
        assertThat(evaluation.score()).isEqualTo(60.0);
    }

    @Test
    void invalidAiWeightSumsTriggerWarningAndFallBackToDefaults(CapturedOutput output) {
        RecruitmentProperties properties = new RecruitmentProperties();
        properties.getScoring().getAi().setEssentialFitWeight(0.50);
        properties.getScoring().getAi().setExperienceFitWeight(0.50);
        properties.getScoring().getAi().setDesirableFitWeight(0.50);
        properties.getScoring().getAi().setDomainFitWeight(0.15);
        properties.getScoring().getAi().setCredentialsFitWeight(0.10);
        properties.afterPropertiesSet();

        assertThat(properties.getResolvedAiScoring(Sector.GENERIC).essentialFitWeight()).isEqualTo(0.35);
        assertThat(output.getOut()).contains("Invalid AI scoring weights sum");
    }

    @Test
    void sectorSpecificAiWeightOverridesAreAppliedWhenSectorMatches() {
        RecruitmentProperties properties = defaultProperties();
        RecruitmentProperties.AiSectorOverride override = new RecruitmentProperties.AiSectorOverride();
        override.setEssentialFitWeight(0.30);
        override.setExperienceFitWeight(0.20);
        override.setDesirableFitWeight(0.15);
        override.setDomainFitWeight(0.10);
        override.setCredentialsFitWeight(0.25);
        properties.getScoring().getAi().setSectorOverrides(Map.of("healthcare", override));
        properties.afterPropertiesSet();
        AiAssessmentToCandidateEvaluationMapper customMapper = new AiAssessmentToCandidateEvaluationMapper(properties);

        CandidateEvaluation evaluation = customMapper.map(
                new CandidateProfile("Alice", "alice.pdf", "", List.of(), null),
                mixedAssessment(),
                Sector.HEALTHCARE
        );

        assertThat(evaluation.score()).isEqualTo(63.3);
    }

    @Test
    void sectorSpecificOverridesAreIgnoredWhenSectorDoesNotMatch() {
        RecruitmentProperties properties = defaultProperties();
        RecruitmentProperties.AiSectorOverride override = new RecruitmentProperties.AiSectorOverride();
        override.setEssentialFitWeight(0.30);
        override.setExperienceFitWeight(0.20);
        override.setDesirableFitWeight(0.15);
        override.setDomainFitWeight(0.10);
        override.setCredentialsFitWeight(0.25);
        properties.getScoring().getAi().setSectorOverrides(Map.of("healthcare", override));
        properties.afterPropertiesSet();
        AiAssessmentToCandidateEvaluationMapper customMapper = new AiAssessmentToCandidateEvaluationMapper(properties);

        CandidateEvaluation evaluation = customMapper.map(
                new CandidateProfile("Alice", "alice.pdf", "", List.of(), null),
                mixedAssessment(),
                Sector.FINANCE
        );

        assertThat(evaluation.score()).isEqualTo(75.0);
    }

    @Test
    void allNullDimensionsFallsBackToLegacyLookup() {
        CandidateProfile profile = new CandidateProfile("Test", "test.pdf", "", List.of(), null);
        AiFitAssessment assessment = new AiFitAssessment(
                MatchBand.STRONG_MATCH, ConfidenceLevel.HIGH,
                null, null, null, null, null,
                List.of(), List.of(), List.of(), ""
        );

        CandidateEvaluation eval = mapper.map(profile, assessment);
        assertThat(eval.score()).isEqualTo(90.0);
    }

    @Test
    void breakdownComponentsSumToFinalScore() {
        CandidateProfile profile = new CandidateProfile("Test", "test.pdf", "", List.of(), null);
        AiFitAssessment assessment = new AiFitAssessment(
                MatchBand.POSSIBLE_MATCH, ConfidenceLevel.MEDIUM,
                new DimensionJudgement(JudgementLevel.STRONG, ""),
                new DimensionJudgement(JudgementLevel.PARTIAL, ""),
                new DimensionJudgement(JudgementLevel.WEAK, ""),
                new DimensionJudgement(JudgementLevel.PARTIAL, ""),
                new DimensionJudgement(JudgementLevel.NONE, ""),
                List.of(), List.of(), List.of(), ""
        );

        CandidateEvaluation eval = mapper.map(profile, assessment);
        double componentSum = eval.scoreBreakdown().skillScore()
                + eval.scoreBreakdown().keywordScore()
                + eval.scoreBreakdown().experienceScore();
        assertThat(componentSum).isCloseTo(eval.score(), within(0.1));
    }

    private CandidateEvaluation mapWithUniformDimensions(JudgementLevel level, ConfidenceLevel confidence) {
        CandidateProfile profile = new CandidateProfile("Test", "test.pdf", "", List.of(), null);
        DimensionJudgement dim = new DimensionJudgement(level, "");
        AiFitAssessment assessment = new AiFitAssessment(
                MatchBand.POSSIBLE_MATCH, confidence,
                dim, dim, dim, dim, dim,
                List.of(), List.of(), List.of(), ""
        );
        return mapper.map(profile, assessment);
    }

    private AiFitAssessment mixedAssessment() {
        return new AiFitAssessment(
                MatchBand.POSSIBLE_MATCH,
                ConfidenceLevel.HIGH,
                new DimensionJudgement(JudgementLevel.STRONG, "Good skills"),
                new DimensionJudgement(JudgementLevel.PARTIAL, "Some desirable"),
                new DimensionJudgement(JudgementLevel.STRONG, "Enough experience"),
                new DimensionJudgement(JudgementLevel.WEAK, "Different domain"),
                new DimensionJudgement(JudgementLevel.NONE, "No certs"),
                List.of(), List.of(), List.of(), ""
        );
    }

    private RecruitmentProperties defaultProperties() {
        RecruitmentProperties properties = new RecruitmentProperties();
        properties.afterPropertiesSet();
        return properties;
    }
}
