package com.recruiter.service;

import com.recruiter.ai.AiAssessmentToCandidateEvaluationMapper;
import com.recruiter.ai.AiResult;
import com.recruiter.ai.AiSkillExtractor;
import com.recruiter.ai.CandidateAiExtractor;
import com.recruiter.ai.ExtractedJobSkills;
import com.recruiter.ai.FitAssessmentAiService;
import com.recruiter.ai.JobDescriptionAiExtractor;
import com.recruiter.ai.TokenUsage;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void emitsProgressEventsWhenListenerIsProvided() {
        CandidateScreeningFacade facade = buildHeuristicFacade(properties(2));
        List<ScreeningProgressEvent> events = new ArrayList<>();

        facade.screen(
                "Senior Java developer with Spring Boot, SQL and AWS. 5 years experience required.",
                1, 0.0, "heuristic",
                List.of(
                        new MockMultipartFile("cvFiles", "alice-smith.pdf", "application/pdf",
                                "Alice Smith\nJava Spring Boot SQL AWS\n6 years experience".getBytes(StandardCharsets.UTF_8)),
                        new MockMultipartFile("cvFiles", "bob-jones.pdf", "application/pdf",
                                "Bob Jones\nJavaScript React CSS\n3 years experience".getBytes(StandardCharsets.UTF_8))
                ),
                events::add
        );

        assertThat(events).extracting(ScreeningProgressEvent::phase)
                .contains("extracting", "scoring", "finalising");
        assertThat(events.stream().filter(event -> "scoring".equals(event.phase()))).hasSize(2);
        assertThat(events.get(events.size() - 1).phase()).isEqualTo("finalising");
    }

    @Test
    void removesDuplicateCandidatesByFingerprintBeforeScoring() {
        CandidateScreeningFacade facade = buildHeuristicFacade(properties(3));

        ScreeningRunResult result = facade.screen(
                "Senior Java developer with Spring Boot, SQL and AWS.",
                3, 0.0, "heuristic",
                List.of(
                        new MockMultipartFile("cvFiles", "alice-original.pdf", "application/pdf",
                                "Alice Smith\nJava Spring Boot SQL AWS\n6 years experience".getBytes(StandardCharsets.UTF_8)),
                        new MockMultipartFile("cvFiles", "alice-copy.pdf", "application/pdf",
                                "Alice Smith\nJava Spring Boot SQL AWS\n6 years experience".getBytes(StandardCharsets.UTF_8))
                )
        );

        assertThat(result.duplicateCvsRemoved()).isEqualTo(1);
        assertThat(result.screeningResult().candidateEvaluations()).hasSize(1);
        assertThat(result.screeningResult().candidateEvaluations().getFirst().candidateProfile().sourceFilename())
                .isEqualTo("alice-original.pdf");
        assertThat(result.wasReduced()).isFalse();
    }

    @Test
    void removesDuplicateCandidatesByFilenameBeforeScoring() {
        CandidateScreeningFacade facade = buildHeuristicFacade(properties(3));

        ScreeningRunResult result = facade.screen(
                "Senior Java developer with Spring Boot, SQL and AWS.",
                3, 0.0, "heuristic",
                List.of(
                        new MockMultipartFile("cvFiles", "candidate.pdf", "application/pdf",
                                "Alice Smith\nJava Spring Boot SQL AWS\n6 years experience".getBytes(StandardCharsets.UTF_8)),
                        new MockMultipartFile("cvFiles", "candidate.pdf", "application/pdf",
                                "Bob Jones\nJavaScript React CSS\n3 years experience".getBytes(StandardCharsets.UTF_8))
                )
        );

        assertThat(result.duplicateCvsRemoved()).isEqualTo(1);
        assertThat(result.screeningResult().candidateEvaluations()).hasSize(1);
        assertThat(result.screeningResult().candidateEvaluations().getFirst().score()).isGreaterThan(0.0);
        assertThat(result.wasReduced()).isFalse();
    }

    @Test
    void persistsEliminatedCandidatesWhenBatchIsReduced() {
        RecruitmentProperties props = properties(2);
        props.setAnalysisCap(2);
        CapturingPersistenceService persistenceService = new CapturingPersistenceService();
        CandidateScreeningFacade facade = buildHeuristicFacade(props, List.of(new StubDocumentExtractionService()), persistenceService);

        ScreeningRunResult result = facade.screen(
                "Senior Java developer with Spring Boot, SQL and AWS. 5 years experience required.",
                2, 0.0, "heuristic",
                List.of(
                        new MockMultipartFile("cvFiles", "alice-smith.pdf", "application/pdf",
                                "Alice Smith\nJava Spring Boot SQL AWS\n6 years experience".getBytes(StandardCharsets.UTF_8)),
                        new MockMultipartFile("cvFiles", "carol-lee.pdf", "application/pdf",
                                "Carol Lee\nJava Spring Boot SQL\n5 years experience".getBytes(StandardCharsets.UTF_8)),
                        new MockMultipartFile("cvFiles", "bob-jones.pdf", "application/pdf",
                                "Bob Jones\nJavaScript React CSS\n3 years experience".getBytes(StandardCharsets.UTF_8))
                )
        );

        assertThat(result.wasReduced()).isTrue();
        assertThat(persistenceService.eliminatedCandidates).hasSize(1);
        assertThat(persistenceService.eliminatedCandidates.getFirst().candidateFilename()).isEqualTo("bob-jones.pdf");
    }

    @Test
    void rescuesBorderlineCandidatesWithinMargin() {
        RecruitmentProperties props = properties(3);
        props.setAnalysisCap(20);
        props.setPrefilterBorderlineMargin(10.0);
        props.setPrefilterMaxRescue(8);
        CapturingPersistenceService persistenceService = new CapturingPersistenceService();
        CandidateScreeningFacade facade = buildHeuristicFacade(props,
                List.of(new StubDocumentExtractionService()), persistenceService);

        String job = "Java SQL AWS Docker Kubernetes Python React Angular TypeScript MongoDB. 5 years experience required.";
        String allSkills = "Java SQL AWS Docker Kubernetes Python React Angular TypeScript MongoDB";
        String nineSkills = "Java SQL AWS Docker Kubernetes Python React Angular TypeScript";
        String eightSkills = "Java SQL AWS Docker Kubernetes Python React Angular";
        String oneSkill = "Java";

        List<MultipartFile> files = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            files.add(candidateFile(i, "TopCandidate" + i, allSkills, 5));
        }
        for (int i = 16; i <= 20; i++) {
            files.add(candidateFile(i, "GoodCandidate" + i, nineSkills, 5));
        }
        for (int i = 21; i <= 23; i++) {
            files.add(candidateFile(i, "BorderlineCandidate" + i, eightSkills, 5));
        }
        for (int i = 24; i <= 25; i++) {
            files.add(candidateFile(i, "WeakCandidate" + i, oneSkill, null));
        }

        ScreeningRunResult result = facade.screen(job, 3, 0.0, "heuristic", files);

        assertThat(result.candidatesScored()).isEqualTo(23);
        assertThat(persistenceService.eliminatedCandidates).hasSize(2);
        assertThat(persistenceService.eliminatedCandidates.stream()
                .map(com.recruiter.persistence.EliminatedCandidateSnapshot::candidateFilename))
                .allMatch(name -> name.startsWith("candidate-24") || name.startsWith("candidate-25"));
    }

    @Test
    void noRescueWhenMarginIsZero() {
        RecruitmentProperties props = properties(3);
        props.setAnalysisCap(20);
        props.setPrefilterBorderlineMargin(0.0);
        props.setPrefilterMaxRescue(8);
        CapturingPersistenceService persistenceService = new CapturingPersistenceService();
        CandidateScreeningFacade facade = buildHeuristicFacade(props,
                List.of(new StubDocumentExtractionService()), persistenceService);

        String job = "Java SQL AWS Docker Kubernetes Python React Angular TypeScript MongoDB. 5 years experience required.";
        String allSkills = "Java SQL AWS Docker Kubernetes Python React Angular TypeScript MongoDB";
        String nineSkills = "Java SQL AWS Docker Kubernetes Python React Angular TypeScript";
        String eightSkills = "Java SQL AWS Docker Kubernetes Python React Angular";
        String oneSkill = "Java";

        List<MultipartFile> files = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            files.add(candidateFile(i, "TopCandidate" + i, allSkills, 5));
        }
        for (int i = 16; i <= 20; i++) {
            files.add(candidateFile(i, "GoodCandidate" + i, nineSkills, 5));
        }
        for (int i = 21; i <= 23; i++) {
            files.add(candidateFile(i, "BorderlineCandidate" + i, eightSkills, 5));
        }
        for (int i = 24; i <= 25; i++) {
            files.add(candidateFile(i, "WeakCandidate" + i, oneSkill, null));
        }

        ScreeningRunResult result = facade.screen(job, 3, 0.0, "heuristic", files);

        assertThat(result.candidatesScored()).isEqualTo(20);
        assertThat(persistenceService.eliminatedCandidates).hasSize(5);
    }

    @Test
    void capsRescueAtMaxRescueLimit() {
        RecruitmentProperties props = properties(3);
        props.setAnalysisCap(20);
        props.setPrefilterBorderlineMargin(15.0);
        props.setPrefilterMaxRescue(5);
        CapturingPersistenceService persistenceService = new CapturingPersistenceService();
        CandidateScreeningFacade facade = buildHeuristicFacade(props,
                List.of(new StubDocumentExtractionService()), persistenceService);

        String job = "Java SQL AWS Docker Kubernetes Python React Angular TypeScript MongoDB. 5 years experience required.";
        String allSkills = "Java SQL AWS Docker Kubernetes Python React Angular TypeScript MongoDB";
        String nineSkills = "Java SQL AWS Docker Kubernetes Python React Angular TypeScript";
        String eightSkills = "Java SQL AWS Docker Kubernetes Python React Angular";
        String oneSkill = "Java";

        List<MultipartFile> files = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            files.add(candidateFile(i, "TopCandidate" + i, allSkills, 5));
        }
        for (int i = 16; i <= 20; i++) {
            files.add(candidateFile(i, "GoodCandidate" + i, nineSkills, 5));
        }
        for (int i = 21; i <= 28; i++) {
            files.add(candidateFile(i, "BorderlineCandidate" + i, eightSkills, 5));
        }
        for (int i = 29; i <= 30; i++) {
            files.add(candidateFile(i, "WeakCandidate" + i, oneSkill, null));
        }

        ScreeningRunResult result = facade.screen(job, 3, 0.0, "heuristic", files);

        assertThat(result.candidatesScored()).isEqualTo(25);
        assertThat(persistenceService.eliminatedCandidates).hasSize(5);
    }

    @Test
    void rescuesBelowFloorCandidateViaSecondaryRuleAndRejectsLowSkillCandidate() {
        RecruitmentProperties props = properties(2);
        props.setAnalysisCap(2);
        props.setPrefilterBorderlineMargin(5.0);
        props.setPrefilterMaxRescue(8);
        CapturingPersistenceService persistenceService = new CapturingPersistenceService();
        CandidateScreeningFacade facade = buildHeuristicFacade(props,
                List.of(new StubDocumentExtractionService()), persistenceService);

        String job = "Java SQL AWS Docker Kubernetes Python. 5 years experience required.";

        List<MultipartFile> files = List.of(
                candidateFile(1, "StrongCandidate", "Java SQL AWS Docker Kubernetes Python", 5),
                candidateFile(2, "GoodCandidate", "Java SQL AWS Docker Kubernetes", 5),
                candidateFile(3, "SkillfulCandidate", "Java SQL AWS Docker", 5),
                candidateFile(4, "SkillfulNoExpCandidate", "Java SQL AWS Docker", null),
                candidateFile(5, "WeakCandidate", "Java", 5)
        );

        ScreeningRunResult result = facade.screen(job, 2, 0.0, "heuristic", files);

        assertThat(result.candidatesScored()).isEqualTo(3);
        assertThat(persistenceService.eliminatedCandidates).hasSize(2);
        assertThat(persistenceService.eliminatedCandidates.stream()
                .map(com.recruiter.persistence.EliminatedCandidateSnapshot::candidateFilename))
                .containsExactlyInAnyOrder("candidate-4.pdf", "candidate-5.pdf");
    }

    @Test
    void totalScoreIsAlwaysBetweenZeroAndOneHundred() {
        RecruitmentProperties props = properties(5);
        props.setAnalysisCap(100);
        CandidateScreeningFacade facade = buildHeuristicFacade(props);

        String job = "Java SQL AWS Docker Kubernetes Python. 5 years experience required.";
        List<MultipartFile> files = List.of(
                candidateFile(1, "FullMatch", "Java SQL AWS Docker Kubernetes Python", 5),
                candidateFile(2, "PartialMatch", "Java SQL", 2),
                candidateFile(3, "NoMatch", "Photography Copywriting", null),
                candidateFile(4, "OverQualified", "Java SQL AWS Docker Kubernetes Python React Angular", 15)
        );

        ScreeningRunResult result = facade.screen(job, 5, 0.0, "heuristic", files);

        assertThat(result.screeningResult().candidateEvaluations())
                .allSatisfy(eval -> {
                    assertThat(eval.score()).isBetween(0.0, 100.0);
                });
    }

    private MockMultipartFile candidateFile(int index, String name, String skillsText, Integer yearsOfExperience) {
        StringBuilder content = new StringBuilder(name).append("\n");
        content.append(skillsText).append("\n");
        if (yearsOfExperience != null) {
            content.append(yearsOfExperience).append(" years experience\n");
        }
        return new MockMultipartFile("cvFiles", "candidate-" + index + ".pdf",
                "application/pdf", content.toString().getBytes(StandardCharsets.UTF_8));
    }

    private CandidateScreeningFacade buildHeuristicFacade(RecruitmentProperties props) {
        return buildHeuristicFacade(props, List.of(new StubDocumentExtractionService()));
    }

    private CandidateScreeningFacade buildHeuristicFacade(RecruitmentProperties props,
                                                           List<DocumentExtractionService> extractors) {
        return buildHeuristicFacade(props, extractors, stubPersistenceService());
    }

    private CandidateScreeningFacade buildHeuristicFacade(RecruitmentProperties props,
                                                          List<DocumentExtractionService> extractors,
                                                          ScreeningBatchPersistenceService persistenceService) {
        TextProfileHeuristicsService heuristicsService = new TextProfileHeuristicsService();
        JobDescriptionProfileFactory jobDescriptionProfileFactory =
                new HeuristicJobDescriptionProfileFactory(heuristicsService);
        return new CandidateScreeningFacade(
                new CvTextExtractionService(extractors),
                jobDescriptionProfileFactory,
                new HeuristicCandidateProfileFactory(heuristicsService),
                new CandidateScoringService(heuristicsService, jobDescriptionProfileFactory),
                heuristicsService,
                new RankingService(),
                new ShortlistService(props),
                persistenceService,
                props,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                new AiAssessmentToCandidateEvaluationMapper(),
                new DirectExecutorService()
        );
    }

    @Test
    void usesAiExtractedDomainSkillsDuringAiPrefiltering() {
        RecruitmentProperties props = properties(1);
        props.setAnalysisCap(1);
        CapturingPersistenceService persistenceService = new CapturingPersistenceService();
        CandidateScreeningFacade facade = buildAiFacade(
                props,
                (jobDescriptionText) -> new AiResult<>(
                        new ExtractedJobSkills(List.of("Solvency II", "Reserving")),
                        new TokenUsage(50, 10, 60)
                ),
                persistenceService
        );

        ScreeningRunResult result = facade.screen(
                "Actuary role focused on Solvency II capital modelling and reserving.",
                1, 0.0, "ai",
                List.of(
                        new MockMultipartFile("cvFiles", "generic.pdf", "application/pdf",
                                "Alex Doe\nFinance analyst\n3 years experience".getBytes(StandardCharsets.UTF_8)),
                        new MockMultipartFile("cvFiles", "actuary.pdf", "application/pdf",
                                "Casey Roe\nActuary\nSolvency II reserving capital modelling\n6 years experience".getBytes(StandardCharsets.UTF_8))
                )
        );

        assertThat(result.wasReduced()).isTrue();
        assertThat(result.screeningResult().candidateEvaluations()).hasSize(1);
        assertThat(result.screeningResult().candidateEvaluations().getFirst().candidateProfile().sourceFilename())
                .isEqualTo("actuary.pdf");
        assertThat(result.aiTokenUsage().totalTokens()).isEqualTo(60);
        assertThat(persistenceService.eliminatedCandidates).hasSize(1);
        assertThat(persistenceService.eliminatedCandidates.getFirst().candidateFilename()).isEqualTo("generic.pdf");
    }

    @Test
    void fallsBackToStaticSkillsWhenAiSkillExtractionFailsDuringPrefiltering() {
        RecruitmentProperties props = properties(1);
        props.setAnalysisCap(1);
        CapturingPersistenceService persistenceService = new CapturingPersistenceService();
        CandidateScreeningFacade facade = buildAiFacade(
                props,
                (jobDescriptionText) -> {
                    throw new IllegalStateException("skill extraction failed");
                },
                persistenceService
        );

        ScreeningRunResult result = facade.screen(
                "Actuary role focused on Solvency II capital modelling and reserving.",
                1, 0.0, "ai",
                List.of(
                        new MockMultipartFile("cvFiles", "generic.pdf", "application/pdf",
                                "Alex Doe\nFinance analyst\n3 years experience".getBytes(StandardCharsets.UTF_8)),
                        new MockMultipartFile("cvFiles", "actuary.pdf", "application/pdf",
                                "Casey Roe\nActuary\nSolvency II reserving capital modelling\n6 years experience".getBytes(StandardCharsets.UTF_8))
                )
        );

        assertThat(result.screeningResult().candidateEvaluations()).hasSize(1);
        assertThat(persistenceService.eliminatedCandidates).hasSize(1);
        assertThat(result.effectiveScoringMode()).isEqualTo(ScoringMode.heuristic);
    }

    private CandidateScreeningFacade buildAiFacade(RecruitmentProperties props,
                                                   AiSkillExtractor aiSkillExtractor,
                                                   ScreeningBatchPersistenceService persistenceService) {
        TextProfileHeuristicsService heuristicsService = new TextProfileHeuristicsService();
        JobDescriptionProfileFactory jobDescriptionProfileFactory =
                new HeuristicJobDescriptionProfileFactory(heuristicsService);
        JobDescriptionAiExtractor failingJobDescriptionAiExtractor =
                jobDescriptionText -> { throw new IllegalStateException("ai disabled for test"); };
        CandidateAiExtractor unusedCandidateAiExtractor =
                cvText -> { throw new IllegalStateException("candidate ai should not run in this test"); };
        FitAssessmentAiService unusedFitAssessmentAiService =
                (job, candidate) -> { throw new IllegalStateException("fit ai should not run in this test"); };

        return new CandidateScreeningFacade(
                new CvTextExtractionService(List.of(new StubDocumentExtractionService())),
                jobDescriptionProfileFactory,
                new HeuristicCandidateProfileFactory(heuristicsService),
                new CandidateScoringService(heuristicsService, jobDescriptionProfileFactory),
                heuristicsService,
                new RankingService(),
                new ShortlistService(props),
                persistenceService,
                props,
                Optional.of(failingJobDescriptionAiExtractor),
                Optional.of(aiSkillExtractor),
                Optional.of(unusedCandidateAiExtractor),
                Optional.of(unusedFitAssessmentAiService),
                new AiAssessmentToCandidateEvaluationMapper(),
                new DirectExecutorService()
        );
    }

    private ScreeningBatchPersistenceService stubPersistenceService() {
        return new ScreeningBatchPersistenceService(null) {
            @Override
            public Long save(String jobDescriptionText, int shortlistCount, ScoringMode scoringMode,
                             int totalCvsReceived, int candidatesScored, double shortlistThreshold,
                             TokenUsage aiTokenUsage, Double aiEstimatedCostUsd, Long processingTimeMs,
                             String aiJobProfileJson, String promptVersions, ScreeningResult screeningResult,
                             List<com.recruiter.persistence.EliminatedCandidateSnapshot> eliminatedCandidates) {
                return 1L;
            }

            @Override
            public void updateProcessingTime(Long batchId, long processingTimeMs) {
            }
        };
    }

    private static final class CapturingPersistenceService extends ScreeningBatchPersistenceService {

        private List<com.recruiter.persistence.EliminatedCandidateSnapshot> eliminatedCandidates = List.of();

        private CapturingPersistenceService() {
            super(null);
        }

        @Override
        public Long save(String jobDescriptionText, int shortlistCount, ScoringMode scoringMode,
                         int totalCvsReceived, int candidatesScored, double shortlistThreshold,
                         TokenUsage aiTokenUsage, Double aiEstimatedCostUsd, Long processingTimeMs,
                         String aiJobProfileJson, String promptVersions, ScreeningResult screeningResult,
                         List<com.recruiter.persistence.EliminatedCandidateSnapshot> eliminatedCandidates) {
            this.eliminatedCandidates = eliminatedCandidates;
            return 1L;
        }

        @Override
        public void updateProcessingTime(Long batchId, long processingTimeMs) {
        }
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

    private static final class DirectExecutorService extends AbstractExecutorService {

        private volatile boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
