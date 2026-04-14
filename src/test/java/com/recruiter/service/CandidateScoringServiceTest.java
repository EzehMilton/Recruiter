package com.recruiter.service;

import com.recruiter.config.RecruitmentProperties;
import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.CandidateProfile;
import com.recruiter.domain.JobDescriptionProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class CandidateScoringServiceTest {

    private final TextProfileHeuristicsService heuristicsService = TextProfileHeuristicsServiceTestSupport.createService();
    private final RecruitmentProperties recruitmentProperties = defaultProperties();
    private final CandidateScoringService scoringService =
            new CandidateScoringService(heuristicsService,
                    new HeuristicJobDescriptionProfileFactory(heuristicsService),
                    recruitmentProperties);

    @Test
    void scoresCandidateFromRawJobDescriptionText() {
        CandidateProfile candidateProfile = new CandidateProfile(
                "Alice Smith",
                "alice-smith.pdf",
                "Alice Smith Java Spring Boot SQL AWS microservices 6 years experience",
                List.of("Java", "Spring Boot", "SQL", "AWS", "Microservices"),
                6
        );

        CandidateEvaluation evaluation = scoringService.evaluate(
                "Senior Java engineer with Spring Boot, SQL and AWS. 5 years experience required.",
                candidateProfile
        );

        assertThat(evaluation.score()).isGreaterThan(60.0);
        assertThat(evaluation.scoreBreakdown().totalWeightedScore()).isEqualTo(evaluation.score());
        assertThat(evaluation.summary()).contains("Matched");
        assertThat(evaluation.summary()).contains("Deterministic score:");
    }

    @Test
    void scoresCandidateAgainstStructuredJobDescriptionProfile() {
        CandidateProfile candidateProfile = new CandidateProfile(
                "Alice Smith",
                "alice-smith.pdf",
                "Alice Smith Java Spring Boot SQL AWS microservices distributed systems 6 years experience",
                List.of("Java", "Spring Boot", "SQL", "AWS", "Microservices"),
                6
        );
        JobDescriptionProfile jobDescriptionProfile = new JobDescriptionProfile(
                "Senior backend engineer",
                List.of("Java", "Spring Boot", "AWS"),
                List.of("distributed", "backend", "systems"),
                5
        );

        CandidateEvaluation evaluation = scoringService.evaluate(jobDescriptionProfile, candidateProfile);

        assertThat(evaluation.score()).isGreaterThan(75.0);
        assertThat(evaluation.scoreBreakdown().skillScore()).isGreaterThan(0.0);
        assertThat(evaluation.scoreBreakdown().keywordScore()).isGreaterThan(0.0);
        assertThat(evaluation.summary()).contains("required keywords");
    }

    @Test
    void summaryCallsOutWeakMatchAreas() {
        CandidateProfile candidateProfile = new CandidateProfile(
                "Bob Jones",
                "bob-jones.pdf",
                "Bob Jones JavaScript React CSS 3 years experience",
                List.of("JavaScript", "React", "CSS"),
                3
        );

        CandidateEvaluation evaluation = scoringService.evaluate(
                "Senior Java engineer with Spring Boot, SQL and AWS. 5 years experience required.",
                candidateProfile
        );

        assertThat(evaluation.summary()).contains("Weak match on");
        assertThat(evaluation.score()).isLessThan(50.0);
        assertThat(evaluation.scoreBreakdown().skillScore()).isEqualTo(0.0);
    }

    @Test
    void defaultHeuristicWeightsProduceCurrentScore() {
        CandidateProfile candidateProfile = new CandidateProfile(
                "Alice Smith",
                "alice-smith.pdf",
                "Alice Smith Java Spring Boot SQL AWS microservices 6 years experience",
                List.of("Java", "Spring Boot", "SQL", "AWS", "Microservices"),
                6
        );

        CandidateEvaluation evaluation = scoringService.evaluate(
                "Senior Java engineer with Spring Boot, SQL and AWS. 5 years experience required.",
                candidateProfile
        );

        assertThat(evaluation.score()).isEqualTo(61.7);
    }

    @Test
    void customHeuristicWeightsChangeScoreProportionally() {
        RecruitmentProperties properties = defaultProperties();
        properties.getScoring().getHeuristic().setEssentialFitMax(30);
        properties.getScoring().getHeuristic().setBroaderSkillFitMax(35);
        properties.getScoring().getHeuristic().setKeywordSupportMax(15);
        properties.getScoring().getHeuristic().setExperienceFitMax(10);
        properties.afterPropertiesSet();
        CandidateScoringService customScoringService = new CandidateScoringService(
                heuristicsService,
                new HeuristicJobDescriptionProfileFactory(heuristicsService),
                properties
        );

        CandidateProfile candidateProfile = new CandidateProfile(
                "Alice Smith",
                "alice-smith.pdf",
                "Alice Smith Java Spring Boot SQL AWS microservices 6 years experience",
                List.of("Java", "Spring Boot", "SQL", "AWS", "Microservices"),
                6
        );

        CandidateEvaluation evaluation = customScoringService.evaluate(
                "Senior Java engineer with Spring Boot, SQL and AWS. 5 years experience required.",
                candidateProfile
        );

        assertThat(evaluation.score()).isEqualTo(62.3);
    }

    @Test
    void invalidHeuristicWeightSumsTriggerWarningAndFallBackToDefaults(CapturedOutput output) {
        RecruitmentProperties properties = new RecruitmentProperties();
        properties.getScoring().getHeuristic().setEssentialFitMax(50);
        properties.getScoring().getHeuristic().setBroaderSkillFitMax(25);
        properties.getScoring().getHeuristic().setKeywordSupportMax(15);
        properties.getScoring().getHeuristic().setExperienceFitMax(10);
        properties.afterPropertiesSet();

        assertThat(properties.getResolvedHeuristicScoring().essentialFitMax()).isEqualTo(40);
        assertThat(output.getOut()).contains("Invalid heuristic scoring weights sum");
    }

    private RecruitmentProperties defaultProperties() {
        RecruitmentProperties properties = new RecruitmentProperties();
        properties.afterPropertiesSet();
        return properties;
    }
}
