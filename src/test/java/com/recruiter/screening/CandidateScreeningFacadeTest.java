package com.recruiter.screening;

import com.recruiter.ai.AiClientException;
import com.recruiter.config.ScoringWeightsProperties;
import com.recruiter.document.CvTextExtractionService;
import com.recruiter.document.DocumentExtractionService;
import com.recruiter.document.ExtractedDocument;
import com.recruiter.config.RecruitmentProperties;
import com.recruiter.domain.ScreenedBatch;
import com.recruiter.domain.ScreeningResult;
import com.recruiter.persistence.ScreeningBatchPersistenceService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CandidateScreeningFacadeTest {

    @Test
    void screensRanksAndShortlistsCandidates() {
        CvTextExtractionService extractionService = new CvTextExtractionService(List.of(new StubDocumentExtractionService()));
        TextProfileHeuristicsService heuristicsService = new TextProfileHeuristicsService();
        ScreeningBatchPersistenceService persistenceService = mock(ScreeningBatchPersistenceService.class);
        when(persistenceService.save(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(ScreeningResult.class)))
                .thenReturn(1L);
        JobDescriptionProfileExtractor jobDescriptionProfileExtractor =
                new HeuristicJobDescriptionProfileExtractor(heuristicsService);
        CandidateProfileExtractor candidateProfileExtractor =
                new HeuristicCandidateProfileExtractor(heuristicsService);
        CandidateScoringService candidateScoringService = new CandidateScoringService(
                heuristicsService,
                jobDescriptionProfileExtractor,
                new CandidateSummaryService(new HeuristicCandidateSummaryGenerator()),
                new CandidateEvaluationFactory(),
                new ScoringWeightsProperties()
        );
        CandidateScreeningFacade facade = new CandidateScreeningFacade(
                extractionService,
                new StubCandidateScreeningOrchestrator(jobDescriptionProfileExtractor, candidateProfileExtractor, candidateScoringService),
                new CandidateEvaluationService(
                        new StubCandidateScreeningOrchestrator(jobDescriptionProfileExtractor, candidateProfileExtractor, candidateScoringService),
                        candidateProfileExtractor,
                        new CandidateEvaluationFactory(),
                        properties(2)),
                new RankingService(),
                new ShortlistService(properties(2)),
                persistenceService
        );

        List<MultipartFile> files = List.of(
                new MockMultipartFile("cvFiles", "alice-smith.pdf", "application/pdf",
                        "Alice Smith\nJava Spring Boot SQL AWS\n6 years experience".getBytes(StandardCharsets.UTF_8)),
                new MockMultipartFile("cvFiles", "bob-jones.pdf", "application/pdf",
                        "Bob Jones\nJavaScript React CSS\n3 years experience".getBytes(StandardCharsets.UTF_8))
        );

        ScreenedBatch screenedBatch = facade.screen(
                "Senior Java developer with Spring Boot, SQL and AWS. 5 years experience required.",
                1,
                files
        );
        ScreeningResult screeningResult = screenedBatch.screeningResult();

        assertThat(screenedBatch.batchId()).isNotNull();
        assertThat(screeningResult.candidateEvaluations()).hasSize(2);
        assertThat(screeningResult.candidateEvaluations().getFirst().candidateProfile().candidateName()).isEqualTo("Alice Smith");
        assertThat(screeningResult.candidateEvaluations().getFirst().shortlisted()).isTrue();
        assertThat(screeningResult.candidateEvaluations().get(1).shortlisted()).isFalse();
        assertThat(screeningResult.candidateEvaluations().getFirst().score())
                .isGreaterThan(screeningResult.candidateEvaluations().get(1).score());
    }

    @Test
    void usesConfiguredShortlistSizeWhenRequestedCountIsMissing() {
        CvTextExtractionService extractionService = new CvTextExtractionService(List.of(new StubDocumentExtractionService()));
        TextProfileHeuristicsService heuristicsService = new TextProfileHeuristicsService();
        ScreeningBatchPersistenceService persistenceService = mock(ScreeningBatchPersistenceService.class);
        when(persistenceService.save(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(ScreeningResult.class)))
                .thenReturn(1L);
        JobDescriptionProfileExtractor jobDescriptionProfileExtractor =
                new HeuristicJobDescriptionProfileExtractor(heuristicsService);
        CandidateProfileExtractor candidateProfileExtractor =
                new HeuristicCandidateProfileExtractor(heuristicsService);
        CandidateScoringService candidateScoringService = new CandidateScoringService(
                heuristicsService,
                jobDescriptionProfileExtractor,
                new CandidateSummaryService(new HeuristicCandidateSummaryGenerator()),
                new CandidateEvaluationFactory(),
                new ScoringWeightsProperties()
        );
        CandidateScreeningFacade facade = new CandidateScreeningFacade(
                extractionService,
                new StubCandidateScreeningOrchestrator(jobDescriptionProfileExtractor, candidateProfileExtractor, candidateScoringService),
                new CandidateEvaluationService(
                        new StubCandidateScreeningOrchestrator(jobDescriptionProfileExtractor, candidateProfileExtractor, candidateScoringService),
                        candidateProfileExtractor,
                        new CandidateEvaluationFactory(),
                        properties(1)),
                new RankingService(),
                new ShortlistService(properties(1)),
                persistenceService
        );

        ScreeningResult screeningResult = facade.screen(
                "Senior Java developer with Spring Boot, SQL and AWS. 5 years experience required.",
                null,
                List.of(
                        new MockMultipartFile("cvFiles", "alice-smith.pdf", "application/pdf",
                                "Alice Smith\nJava Spring Boot SQL AWS\n6 years experience".getBytes(StandardCharsets.UTF_8)),
                        new MockMultipartFile("cvFiles", "bob-jones.pdf", "application/pdf",
                                "Bob Jones\nJavaScript React CSS\n3 years experience".getBytes(StandardCharsets.UTF_8))
                )
        ).screeningResult();

        assertThat(screeningResult.shortlistedCandidates()).hasSize(1);
        assertThat(screeningResult.candidateEvaluations().getFirst().shortlisted()).isTrue();
        assertThat(screeningResult.candidateEvaluations().get(1).shortlisted()).isFalse();
    }

    @Test
    void recordsFailedCandidateAndContinuesScreeningRemainingFiles() {
        CvTextExtractionService extractionService = new CvTextExtractionService(List.of(new PartiallyFailingDocumentExtractionService()));
        TextProfileHeuristicsService heuristicsService = new TextProfileHeuristicsService();
        ScreeningBatchPersistenceService persistenceService = mock(ScreeningBatchPersistenceService.class);
        when(persistenceService.save(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(ScreeningResult.class)))
                .thenReturn(1L);
        JobDescriptionProfileExtractor jobDescriptionProfileExtractor =
                new HeuristicJobDescriptionProfileExtractor(heuristicsService);
        CandidateProfileExtractor candidateProfileExtractor =
                new HeuristicCandidateProfileExtractor(heuristicsService);
        CandidateScoringService candidateScoringService = new CandidateScoringService(
                heuristicsService,
                jobDescriptionProfileExtractor,
                new CandidateSummaryService(new HeuristicCandidateSummaryGenerator()),
                new CandidateEvaluationFactory(),
                new ScoringWeightsProperties()
        );
        CandidateScreeningFacade facade = new CandidateScreeningFacade(
                extractionService,
                new StubCandidateScreeningOrchestrator(jobDescriptionProfileExtractor, candidateProfileExtractor, candidateScoringService),
                new CandidateEvaluationService(
                        new StubCandidateScreeningOrchestrator(jobDescriptionProfileExtractor, candidateProfileExtractor, candidateScoringService),
                        candidateProfileExtractor,
                        new CandidateEvaluationFactory(),
                        properties(2)),
                new RankingService(),
                new ShortlistService(properties(2)),
                persistenceService
        );

        ScreeningResult screeningResult = facade.screen(
                "Senior Java developer with Spring Boot, SQL and AWS. 5 years experience required.",
                2,
                List.of(
                        new MockMultipartFile("cvFiles", "alice-smith.pdf", "application/pdf",
                                "Alice Smith\nJava Spring Boot SQL AWS\n6 years experience".getBytes(StandardCharsets.UTF_8)),
                        new MockMultipartFile("cvFiles", "broken-cv.pdf", "application/pdf",
                                "broken".getBytes(StandardCharsets.UTF_8))
                )
        ).screeningResult();

        assertThat(screeningResult.candidateEvaluations()).hasSize(2);
        assertThat(screeningResult.candidateEvaluations().getFirst().score()).isGreaterThan(0.0);
        assertThat(screeningResult.candidateEvaluations().get(1).candidateProfile().sourceFilename()).isEqualTo("broken-cv.pdf");
        assertThat(screeningResult.candidateEvaluations().get(1).score()).isEqualTo(0.0);
        assertThat(screeningResult.candidateEvaluations().get(1).summary()).contains("CV extraction failed");
        assertThat(screeningResult.candidateEvaluations().get(1).shortlisted()).isFalse();
    }

    @Test
    void recordsFailedCandidateWhenAiCandidateExtractionFailsAndContinuesBatch() {
        CvTextExtractionService extractionService = new CvTextExtractionService(List.of(new StubDocumentExtractionService()));
        TextProfileHeuristicsService heuristicsService = new TextProfileHeuristicsService();
        ScreeningBatchPersistenceService persistenceService = mock(ScreeningBatchPersistenceService.class);
        when(persistenceService.save(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(ScreeningResult.class)))
                .thenReturn(1L);
        JobDescriptionProfileExtractor jobDescriptionProfileExtractor =
                new HeuristicJobDescriptionProfileExtractor(heuristicsService);
        CandidateProfileExtractor candidateProfileExtractor = new FailingCandidateProfileExtractor();
        CandidateScoringService candidateScoringService = new CandidateScoringService(
                heuristicsService,
                jobDescriptionProfileExtractor,
                new CandidateSummaryService(new HeuristicCandidateSummaryGenerator()),
                new CandidateEvaluationFactory(),
                new ScoringWeightsProperties()
        );
        CandidateScreeningFacade facade = new CandidateScreeningFacade(
                extractionService,
                new StubCandidateScreeningOrchestrator(jobDescriptionProfileExtractor, candidateProfileExtractor, candidateScoringService),
                new CandidateEvaluationService(
                        new StubCandidateScreeningOrchestrator(jobDescriptionProfileExtractor, candidateProfileExtractor, candidateScoringService),
                        candidateProfileExtractor,
                        new CandidateEvaluationFactory(),
                        properties(2)),
                new RankingService(),
                new ShortlistService(properties(2)),
                persistenceService
        );

        ScreeningResult screeningResult = facade.screen(
                "Senior Java developer with Spring Boot and AWS.",
                2,
                List.of(
                        new MockMultipartFile("cvFiles", "ok.pdf", "application/pdf",
                                "OK Candidate\nJava Spring Boot AWS\n6 years experience".getBytes(StandardCharsets.UTF_8)),
                        new MockMultipartFile("cvFiles", "ai-fail.pdf", "application/pdf",
                                "Broken Candidate\nJava".getBytes(StandardCharsets.UTF_8))
                )
        ).screeningResult();

        assertThat(screeningResult.candidateEvaluations()).hasSize(2);
        assertThat(screeningResult.candidateEvaluations().get(1).candidateProfile().sourceFilename()).isEqualTo("ai-fail.pdf");
        assertThat(screeningResult.candidateEvaluations().get(1).score()).isEqualTo(0.0);
        assertThat(screeningResult.candidateEvaluations().get(1).summary()).contains("AI candidate processing failed");
    }

    private RecruitmentProperties properties(int shortlistCount) {
        RecruitmentProperties properties = new RecruitmentProperties();
        properties.setShortlistCount(shortlistCount);
        properties.setMaxJobDescriptionWords(1000);
        properties.setMaxCandidates(20);
        properties.setMaxParallelCandidateEvaluations(4);
        properties.setMaxFileSizeBytes(5 * 1024 * 1024);
        return properties;
    }

    private static final class StubDocumentExtractionService implements DocumentExtractionService {

        @Override
        public boolean supports(MultipartFile file) {
            return true;
        }

        @Override
        public ExtractedDocument extract(MultipartFile file) {
            return new ExtractedDocument(
                    file.getOriginalFilename(),
                    new String(readBytes(file), StandardCharsets.UTF_8)
            );
        }

        private byte[] readBytes(MultipartFile file) {
            try {
                return file.getBytes();
            } catch (java.io.IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    private static final class PartiallyFailingDocumentExtractionService implements DocumentExtractionService {

        @Override
        public boolean supports(MultipartFile file) {
            return true;
        }

        @Override
        public ExtractedDocument extract(MultipartFile file) {
            if ("broken-cv.pdf".equals(file.getOriginalFilename())) {
                throw new com.recruiter.document.DocumentExtractionException(
                        "Failed to extract text from PDF 'broken-cv.pdf'.");
            }
            return new ExtractedDocument(
                    file.getOriginalFilename(),
                    new String(readBytes(file), StandardCharsets.UTF_8)
            );
        }

        private byte[] readBytes(MultipartFile file) {
            try {
                return file.getBytes();
            } catch (java.io.IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    private static final class FailingCandidateProfileExtractor implements CandidateProfileExtractor {

        @Override
        public com.recruiter.domain.CandidateProfile extract(ExtractedDocument extractedDocument) {
            if ("ai-fail.pdf".equals(extractedDocument.originalFilename())) {
                throw new AiClientException("simulated candidate AI failure");
            }
            return new com.recruiter.domain.CandidateProfile(
                    "OK Candidate",
                    extractedDocument.originalFilename(),
                    extractedDocument.text(),
                    List.of("Java", "Spring Boot", "AWS"),
                    List.of(),
                    List.of(),
                    6
            );
        }
    }

    private static final class StubCandidateScreeningOrchestrator implements CandidateScreeningOrchestrator {

        private final JobDescriptionProfileExtractor jobDescriptionProfileExtractor;
        private final CandidateProfileExtractor candidateProfileExtractor;
        private final CandidateScoringService candidateScoringService;

        private StubCandidateScreeningOrchestrator(JobDescriptionProfileExtractor jobDescriptionProfileExtractor,
                                                   CandidateProfileExtractor candidateProfileExtractor,
                                                   CandidateScoringService candidateScoringService) {
            this.jobDescriptionProfileExtractor = jobDescriptionProfileExtractor;
            this.candidateProfileExtractor = candidateProfileExtractor;
            this.candidateScoringService = candidateScoringService;
        }

        @Override
        public com.recruiter.domain.JobDescriptionProfile extractJobDescriptionProfile(String jobDescriptionText) {
            return jobDescriptionProfileExtractor.extract(jobDescriptionText);
        }

        @Override
        public com.recruiter.domain.CandidateEvaluation evaluateCandidate(com.recruiter.domain.JobDescriptionProfile jobDescriptionProfile,
                                                                          ExtractedDocument extractedDocument) {
            return candidateScoringService.evaluate(
                    jobDescriptionProfile,
                    candidateProfileExtractor.extract(extractedDocument)
            );
        }
    }
}
