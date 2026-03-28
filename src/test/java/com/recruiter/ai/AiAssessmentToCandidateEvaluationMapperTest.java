package com.recruiter.ai;

import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.CandidateProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiAssessmentToCandidateEvaluationMapperTest {

    private final AiAssessmentToCandidateEvaluationMapper mapper = new AiAssessmentToCandidateEvaluationMapper();

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
    void mapsMatchBandAndConfidenceToExpectedScore(MatchBand band, ConfidenceLevel confidence, double expectedScore) {
        assertThat(mapper.mapToInternalScore(band, confidence)).isEqualTo(expectedScore);
    }

    @Test
    void mapsBuildsCandidateEvaluationWithMappedScoreAndAiSummary() {
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

        assertThat(evaluation.score()).isEqualTo(72.0);
        assertThat(evaluation.scoreBreakdown().skillScore()).isEqualTo(72.0);
        assertThat(evaluation.scoreBreakdown().keywordScore()).isEqualTo(0.0);
        assertThat(evaluation.scoreBreakdown().experienceScore()).isEqualTo(0.0);
        assertThat(evaluation.summary()).isEqualTo("Solid Java developer with good experience but missing AWS.");
        assertThat(evaluation.shortlisted()).isFalse();
        assertThat(evaluation.scoringPath()).isEqualTo("ai");
        assertThat(evaluation.aiConfidence()).isEqualTo("HIGH");
        assertThat(evaluation.aiTopStrengths()).containsExactly("Java", "Spring");
        assertThat(evaluation.aiTopGaps()).containsExactly("No AWS");
        assertThat(evaluation.aiInterviewProbeAreas()).containsExactly("Ask about cloud");
    }
}
