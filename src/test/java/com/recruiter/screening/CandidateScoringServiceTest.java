package com.recruiter.screening;

import com.recruiter.config.ScoringWeightsProperties;
import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.CandidateProfile;
import com.recruiter.domain.JobDescriptionProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateScoringServiceTest {

    private final TextProfileHeuristicsService heuristicsService = new TextProfileHeuristicsService();
    private final CandidateScoringService scoringService =
            new CandidateScoringService(
                    heuristicsService,
                    new HeuristicJobDescriptionProfileExtractor(heuristicsService),
                    new CandidateSummaryService(new HeuristicCandidateSummaryGenerator()),
                    new CandidateEvaluationFactory(),
                    new ScoringWeightsProperties()
            );

    @Test
    void scoresCandidateFromRawJobDescriptionText() {
        CandidateProfile candidateProfile = new CandidateProfile(
                "Alice Smith",
                "alice-smith.pdf",
                "Alice Smith Java Spring Boot SQL AWS microservices 6 years experience",
                List.of("Java", "Spring Boot", "SQL", "AWS", "Microservices"),
                List.of(),
                List.of(),
                6
        );

        CandidateEvaluation evaluation = scoringService.evaluate(
                "Senior Java engineer with Spring Boot, SQL and AWS. 5 years experience required.",
                candidateProfile
        );

        assertThat(evaluation.score()).isGreaterThan(70.0);
        assertThat(evaluation.summary()).isNotBlank();
        assertThat(evaluation.scoreDetails()).isNotNull();
        assertThat(evaluation.scoreDetails().requiredSkillsScore()).isGreaterThan(0.0);
    }

    @Test
    void scoresCandidateAgainstStructuredJobDescriptionProfile() {
        CandidateProfile candidateProfile = new CandidateProfile(
                "Alice Smith",
                "alice-smith.pdf",
                "Alice Smith Java Spring Boot SQL AWS microservices distributed systems 6 years experience",
                List.of("Java", "Spring Boot", "SQL", "AWS", "Microservices"),
                List.of(),
                List.of(),
                6
        );
        JobDescriptionProfile jobDescriptionProfile = new JobDescriptionProfile(
                "Senior backend engineer",
                List.of("Java", "Spring Boot", "AWS"),
                List.of(),
                List.of(),
                List.of(),
                List.of("distributed", "backend", "systems"),
                5
        );

        CandidateEvaluation evaluation = scoringService.evaluate(jobDescriptionProfile, candidateProfile);

        assertThat(evaluation.score()).isGreaterThan(75.0);
        assertThat(evaluation.scoreDetails().matchedRequiredSkills()).contains("Java", "Spring Boot", "AWS");
    }

    @Test
    void summaryCallsOutWeakMatchAreas() {
        CandidateProfile candidateProfile = new CandidateProfile(
                "Bob Jones",
                "bob-jones.pdf",
                "Bob Jones JavaScript React CSS 3 years experience",
                List.of("JavaScript", "React", "CSS"),
                List.of(),
                List.of(),
                3
        );

        CandidateEvaluation evaluation = scoringService.evaluate(
                "Senior Java engineer with Spring Boot, SQL and AWS. 5 years experience required.",
                candidateProfile
        );

        assertThat(evaluation.scoreDetails().missingRequiredSkills()).isNotEmpty();
        assertThat(evaluation.score()).isLessThan(50.0);
    }

    @Test
    void computesWeightedScoreAcrossAllCategories() {
        CandidateProfile candidateProfile = new CandidateProfile(
                "Full Match",
                "full-match.pdf",
                "Full Match Java Spring Boot AWS Bachelor's Computer Science communication teamwork distributed backend 7 years experience",
                List.of("Java", "Spring Boot", "AWS"),
                List.of("Bachelor's", "Computer Science"),
                List.of("Communication", "Teamwork"),
                7
        );
        JobDescriptionProfile jobDescriptionProfile = new JobDescriptionProfile(
                "Senior Java engineer",
                List.of("Java", "Spring Boot", "AWS"),
                List.of("Docker"),
                List.of("Bachelor's", "Computer Science"),
                List.of("Communication", "Teamwork"),
                List.of("distributed", "backend"),
                5
        );

        CandidateScoreDetails details = scoringService.score(jobDescriptionProfile, candidateProfile);

        assertThat(details.requiredSkillsScore()).isEqualTo(100.0);
        assertThat(details.qualificationsScore()).isEqualTo(100.0);
        assertThat(details.softSkillsScore()).isEqualTo(100.0);
        assertThat(details.experienceScore()).isEqualTo(100.0);
        assertThat(details.totalScore()).isGreaterThan(80.0);

        assertThat(details.categoryBreakdown()).hasSize(6);
        Map<String, CategoryScore> byLabel = details.categoryBreakdown().stream()
                .collect(Collectors.toMap(CategoryScore::label, c -> c));
        assertThat(byLabel.get("Required Skills").effectiveWeight()).isEqualTo(30);
        assertThat(byLabel.get("Required Skills").contribution()).isGreaterThan(0.0);
        assertThat(byLabel.get("Preferred Skills").effectiveWeight()).isEqualTo(15);

        double sumContributions = details.categoryBreakdown().stream()
                .mapToDouble(CategoryScore::contribution)
                .sum();
        assertThat(sumContributions).isCloseTo(details.totalScore(), org.assertj.core.data.Offset.offset(0.2));
    }

    @Test
    void redistributesWeightWhenCategoryIsAbsentFromJobDescription() {
        CandidateProfile candidateProfile = new CandidateProfile(
                "Alice",
                "alice.pdf",
                "Alice Java Spring Boot AWS 6 years experience",
                List.of("Java", "Spring Boot", "AWS"),
                List.of(),
                List.of(),
                6
        );
        JobDescriptionProfile jobWithOnlyRequiredSkills = new JobDescriptionProfile(
                "Java engineer",
                List.of("Java", "Spring Boot", "AWS"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        );

        CandidateScoreDetails details = scoringService.score(jobWithOnlyRequiredSkills, candidateProfile);

        assertThat(details.requiredSkillsScore()).isEqualTo(100.0);
        assertThat(details.totalScore()).isEqualTo(100.0);

        Map<String, CategoryScore> byLabel = details.categoryBreakdown().stream()
                .collect(Collectors.toMap(CategoryScore::label, c -> c));
        assertThat(byLabel.get("Required Skills").effectiveWeight()).isEqualTo(30);
        assertThat(byLabel.get("Preferred Skills").effectiveWeight()).isEqualTo(0);
        assertThat(byLabel.get("Experience").effectiveWeight()).isEqualTo(0);
    }
}
