package com.recruiter.screening;

import com.recruiter.ai.AiAssessmentToCandidateEvaluationMapper;
import com.recruiter.document.CvTextExtractionService;
import com.recruiter.document.DocumentExtractionService;
import com.recruiter.document.ExtractedDocument;
import com.recruiter.config.RecruitmentProperties;
import com.recruiter.domain.ScoringMode;
import com.recruiter.domain.ScreeningResult;
import com.recruiter.domain.ScreeningRunResult;
import com.recruiter.persistence.ScreeningBatchPersistenceService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CandidateScreeningFacadeTest {

    @Test
    void screensRanksAndShortlistsCandidatesInHeuristicMode() {
        CandidateScreeningFacade facade = buildHeuristicFacade(properties(2));

        List<MultipartFile> files = List.of(
                new MockMultipartFile("cvFiles", "alice-smith.pdf", "application/pdf",
                        "Alice Smith\nJava Spring Boot SQL AWS\n6 years experience".getBytes(StandardCharsets.UTF_8)),
                new MockMultipartFile("cvFiles", "bob-jones.pdf", "application/pdf",
                        "Bob Jones\nJavaScript React CSS\n3 years experience".getBytes(StandardCharsets.UTF_8))
        );

        ScreeningRunResult result = facade.screen(
                "Senior Java developer with Spring Boot, SQL and AWS. 5 years experience required.",
                1, 0.0, "heuristic", files
        );
        ScreeningResult screeningResult = result.screeningResult();

        assertThat(result.effectiveScoringMode()).isEqualTo(ScoringMode.heuristic);
        assertThat(screeningResult.candidateEvaluations()).hasSize(2);
        assertThat(screeningResult.candidateEvaluations().getFirst().candidateProfile().candidateName()).isEqualTo("Alice Smith");
        assertThat(screeningResult.candidateEvaluations().getFirst().shortlisted()).isTrue();
        assertThat(screeningResult.candidateEvaluations().get(1).shortlisted()).isFalse();
        assertThat(screeningResult.candidateEvaluations().getFirst().score())
                .isGreaterThan(screeningResult.candidateEvaluations().get(1).score());
    }

    @Test
    void fallsBackToHeuristicWhenAiRequestedButNotConfigured() {
        CandidateScreeningFacade facade = buildHeuristicFacade(properties(2));

        ScreeningRunResult result = facade.screen(
                "Senior Java developer with Spring Boot, SQL and AWS.",
                1, 0.0, "ai",
                List.of(new MockMultipartFile("cvFiles", "alice.pdf", "application/pdf",
                        "Alice Smith\nJava Spring Boot\n5 years".getBytes(StandardCharsets.UTF_8)))
        );

        assertThat(result.effectiveScoringMode()).isEqualTo(ScoringMode.heuristic);
        assertThat(result.screeningResult().candidateEvaluations()).hasSize(1);
        assertThat(result.screeningResult().candidateEvaluations().getFirst().score()).isGreaterThan(0.0);
    }

    @Test
    void usesConfiguredShortlistSizeWhenRequestedCountIsMissing() {
        CandidateScreeningFacade facade = buildHeuristicFacade(properties(1));

        ScreeningRunResult result = facade.screen(
                "Senior Java developer with Spring Boot, SQL and AWS. 5 years experience required.",
                null, null, "heuristic",
                List.of(
                        new MockMultipartFile("cvFiles", "alice-smith.pdf", "application/pdf",
                                "Alice Smith\nJava Spring Boot SQL AWS\n6 years experience".getBytes(StandardCharsets.UTF_8)),
                        new MockMultipartFile("cvFiles", "bob-jones.pdf", "application/pdf",
                                "Bob Jones\nJavaScript React CSS\n3 years experience".getBytes(StandardCharsets.UTF_8))
                )
        );
        ScreeningResult screeningResult = result.screeningResult();

        assertThat(screeningResult.shortlistedCandidates()).hasSize(1);
        assertThat(result.shortlistCount()).isEqualTo(1);
        assertThat(screeningResult.candidateEvaluations().getFirst().shortlisted()).isTrue();
        assertThat(screeningResult.candidateEvaluations().get(1).shortlisted()).isFalse();
    }

    @Test
    void recordsFailedCandidateAndContinuesScreeningRemainingFiles() {
        CandidateScreeningFacade facade = buildHeuristicFacade(properties(2),
                List.of(new PartiallyFailingDocumentExtractionService()));

        ScreeningRunResult result = facade.screen(
                "Senior Java developer with Spring Boot, SQL and AWS. 5 years experience required.",
                2, 1.0, "heuristic",
                List.of(
                        new MockMultipartFile("cvFiles", "alice-smith.pdf", "application/pdf",
                                "Alice Smith\nJava Spring Boot SQL AWS\n6 years experience".getBytes(StandardCharsets.UTF_8)),
                        new MockMultipartFile("cvFiles", "broken-cv.pdf", "application/pdf",
                                "broken".getBytes(StandardCharsets.UTF_8))
                )
        );
        ScreeningResult screeningResult = result.screeningResult();

        assertThat(screeningResult.candidateEvaluations()).hasSize(2);
        assertThat(screeningResult.candidateEvaluations().getFirst().score()).isGreaterThan(0.0);
        assertThat(screeningResult.candidateEvaluations().get(1).candidateProfile().sourceFilename()).isEqualTo("broken-cv.pdf");
        assertThat(screeningResult.candidateEvaluations().get(1).score()).isEqualTo(0.0);
        assertThat(screeningResult.candidateEvaluations().get(1).summary()).contains("CV extraction failed");
        assertThat(screeningResult.candidateEvaluations().get(1).shortlisted()).isFalse();
    }

    private CandidateScreeningFacade buildHeuristicFacade(RecruitmentProperties props) {
        return buildHeuristicFacade(props, List.of(new StubDocumentExtractionService()));
    }

    private CandidateScreeningFacade buildHeuristicFacade(RecruitmentProperties props,
                                                           List<DocumentExtractionService> extractors) {
        TextProfileHeuristicsService heuristicsService = new TextProfileHeuristicsService();
        ScreeningBatchPersistenceService persistenceService = mock(ScreeningBatchPersistenceService.class);
        when(persistenceService.save(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(ScoringMode.class),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(ScreeningResult.class)))
                .thenReturn(1L);
        JobDescriptionProfileFactory jobDescriptionProfileFactory =
                new HeuristicJobDescriptionProfileFactory(heuristicsService);
        return new CandidateScreeningFacade(
                new CvTextExtractionService(extractors),
                jobDescriptionProfileFactory,
                new HeuristicCandidateProfileFactory(heuristicsService),
                new CandidateScoringService(heuristicsService, jobDescriptionProfileFactory),
                new RankingService(),
                new ShortlistService(props),
                persistenceService,
                props,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                new AiAssessmentToCandidateEvaluationMapper()
        );
    }

    private RecruitmentProperties properties(int shortlistCount) {
        RecruitmentProperties properties = new RecruitmentProperties();
        properties.setShortlistCount(shortlistCount);
        properties.setMaxJobDescriptionWords(1000);
        properties.setAnalysisCap(20);
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
}
