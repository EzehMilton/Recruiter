package com.recruiter.screening;

import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.CandidateProfile;
import com.recruiter.domain.JobDescriptionProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateScoringServiceTest {

    private final TextProfileHeuristicsService heuristicsService = new TextProfileHeuristicsService();
    private final CandidateScoringService scoringService =
            new CandidateScoringService(heuristicsService, new HeuristicJobDescriptionProfileFactory(heuristicsService));

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

        assertThat(evaluation.score()).isGreaterThan(70.0);
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
    }
}
