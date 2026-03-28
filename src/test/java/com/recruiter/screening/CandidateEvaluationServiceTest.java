package com.recruiter.screening;

import com.recruiter.config.RecruitmentProperties;
import com.recruiter.config.ScoringWeightsProperties;
import com.recruiter.document.DocumentExtractionOutcome;
import com.recruiter.document.ExtractedDocument;
import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.JobDescriptionProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateEvaluationServiceTest {

    @Test
    void reusesEvaluationWhenTwoFilesHaveIdenticalExtractedText() {
        AtomicInteger evaluationCount = new AtomicInteger();
        TextProfileHeuristicsService heuristicsService = new TextProfileHeuristicsService();
        JobDescriptionProfileExtractor jobExtractor = new HeuristicJobDescriptionProfileExtractor(heuristicsService);
        CandidateProfileExtractor candidateExtractor = new HeuristicCandidateProfileExtractor(heuristicsService);
        CandidateScoringService scoringService = new CandidateScoringService(
                heuristicsService, jobExtractor,
                new CandidateSummaryService(new HeuristicCandidateSummaryGenerator()),
                new CandidateEvaluationFactory(), new ScoringWeightsProperties()
        );

        CandidateScreeningOrchestrator countingOrchestrator = new CandidateScreeningOrchestrator() {
            private final CandidateScreeningOrchestrator delegate = new CandidateScreeningOrchestrator() {
                @Override
                public JobDescriptionProfile extractJobDescriptionProfile(String text) {
                    return jobExtractor.extract(text);
                }

                @Override
                public CandidateEvaluation evaluateCandidate(JobDescriptionProfile jobProfile, ExtractedDocument doc) {
                    return scoringService.evaluate(jobProfile, candidateExtractor.extract(doc));
                }
            };

            @Override
            public JobDescriptionProfile extractJobDescriptionProfile(String text) {
                return delegate.extractJobDescriptionProfile(text);
            }

            @Override
            public CandidateEvaluation evaluateCandidate(JobDescriptionProfile jobProfile, ExtractedDocument doc) {
                evaluationCount.incrementAndGet();
                return delegate.evaluateCandidate(jobProfile, doc);
            }
        };

        CandidateEvaluationService service = new CandidateEvaluationService(
                countingOrchestrator, candidateExtractor, new CandidateEvaluationFactory(), properties()
        );

        String identicalText = "Alice Smith\nJava Spring Boot AWS\n6 years experience";
        JobDescriptionProfile jobProfile = jobExtractor.extract(
                "Senior Java developer with Spring Boot and AWS. 5 years experience required.");

        List<DocumentExtractionOutcome> outcomes = List.of(
                DocumentExtractionOutcome.success(new ExtractedDocument("alice-v1.pdf", identicalText)),
                DocumentExtractionOutcome.success(new ExtractedDocument("alice-v2.pdf", identicalText)),
                DocumentExtractionOutcome.success(new ExtractedDocument("bob.pdf",
                        "Bob Jones\nJavaScript React CSS\n3 years experience"))
        );

        List<CandidateEvaluation> evaluations = service.evaluateAll(jobProfile, outcomes);

        assertThat(evaluations).hasSize(3);
        assertThat(evaluationCount.get()).isEqualTo(2)
                .as("identical text should be evaluated only once, not twice");
    }

    private RecruitmentProperties properties() {
        RecruitmentProperties props = new RecruitmentProperties();
        props.setMaxParallelCandidateEvaluations(4);
        return props;
    }
}
