package com.recruiter.service;

import com.recruiter.ai.AiAssessmentToCandidateEvaluationMapper;
import com.recruiter.ai.AiCandidateProfile;
import com.recruiter.ai.AiFitAssessment;
import com.recruiter.ai.AiJobDescriptionProfile;
import com.recruiter.ai.AiModelSelectionService;
import com.recruiter.ai.AiResult;
import com.recruiter.ai.AiSkillExtractor;
import com.recruiter.ai.CandidateAiExtractor;
import com.recruiter.ai.ExtractedJobSkills;
import com.recruiter.ai.FitAssessmentAiService;
import com.recruiter.ai.JobDescriptionAiExtractor;
import com.recruiter.ai.PromptProviderFactory;
import com.recruiter.ai.Sector;
import com.recruiter.ai.SectorSkillDictionary;
import com.recruiter.ai.TokenUsage;
import com.recruiter.ai.TokenUsageAccumulator;
import com.recruiter.config.RecruitmentProperties;
import com.recruiter.document.CvTextExtractionService;
import com.recruiter.document.DocumentExtractionOutcome;
import com.recruiter.document.ExtractedDocument;
import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.CandidateProfile;
import com.recruiter.domain.JobDescriptionProfile;
import com.recruiter.domain.ScoringMode;
import com.recruiter.domain.ScreeningPackage;
import com.recruiter.domain.ScreeningRunResult;
import com.recruiter.domain.ScreeningResult;
import com.recruiter.persistence.EliminatedCandidateSnapshot;
import com.recruiter.persistence.ScreeningBatchPersistenceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class CandidateScreeningFacade {

    private static final Logger log = LoggerFactory.getLogger(CandidateScreeningFacade.class);

    private final CvTextExtractionService cvTextExtractionService;
    private final JobDescriptionProfileFactory jobDescriptionProfileFactory;
    private final CandidateProfileFactory candidateProfileFactory;
    private final CandidateScoringService candidateScoringService;
    private final TextProfileHeuristicsService heuristicsService;
    private final CvDeduplicationService cvDeduplicationService;
    private final RankingService rankingService;
    private final ShortlistService shortlistService;
    private final ScreeningBatchPersistenceService screeningBatchPersistenceService;
    private final RecruitmentProperties properties;
    private final AiModelSelectionService aiModelSelectionService;
    private final Optional<JobDescriptionAiExtractor> jobDescriptionAiExtractor;
    private final Optional<AiSkillExtractor> aiSkillExtractor;
    private final Optional<CandidateAiExtractor> candidateAiExtractor;
    private final Optional<FitAssessmentAiService> fitAssessmentAiService;
    private final AiAssessmentToCandidateEvaluationMapper aiMapper;
    private final PromptProviderFactory promptProviderFactory;
    private final ExecutorService screeningVirtualExecutor;
    private volatile Semaphore screeningRunSemaphore;
    private volatile Semaphore aiCandidateEvaluationSemaphore;

    public ScreeningRunResult screen(String jobDescription, Integer shortlistCount,
                                      Double minimumShortlistScore, String requestedScoringMode,
                                      List<MultipartFile> cvFiles) {
        return screen(jobDescription, shortlistCount, minimumShortlistScore, requestedScoringMode,
                cvFiles, null, ScreeningPackage.QUICK_SCREEN, null, null);
    }

    public ScreeningRunResult screen(String jobDescription, Integer shortlistCount,
                                      Double minimumShortlistScore, String requestedScoringMode,
                                      List<MultipartFile> cvFiles,
                                      ScreeningProgressListener progressListener) {
        return screen(jobDescription, shortlistCount, minimumShortlistScore, requestedScoringMode,
                cvFiles, progressListener, ScreeningPackage.QUICK_SCREEN, null, null);
    }

    public ScreeningRunResult screen(String jobDescription, Integer shortlistCount,
                                      Double minimumShortlistScore, String requestedScoringMode,
                                      List<MultipartFile> cvFiles,
                                      String requestedSector) {
        return screen(jobDescription, shortlistCount, minimumShortlistScore, requestedScoringMode,
                cvFiles, null, ScreeningPackage.QUICK_SCREEN, requestedSector, null);
    }

    public ScreeningRunResult screen(String jobDescription, Integer shortlistCount,
                                      Double minimumShortlistScore, String requestedScoringMode,
                                      List<MultipartFile> cvFiles,
                                      ScreeningProgressListener progressListener,
                                      String requestedSector) {
        return screen(jobDescription, shortlistCount, minimumShortlistScore, requestedScoringMode,
                cvFiles, progressListener, ScreeningPackage.QUICK_SCREEN, requestedSector, null);
    }

    public ScreeningRunResult screen(String jobDescription, Integer shortlistCount,
                                      Double minimumShortlistScore, String requestedScoringMode,
                                      List<MultipartFile> cvFiles,
                                      String requestedSector,
                                      Integer overrideAnalysisCap) {
        return screen(jobDescription, shortlistCount, minimumShortlistScore, requestedScoringMode,
                cvFiles, null, ScreeningPackage.QUICK_SCREEN, requestedSector, overrideAnalysisCap);
    }

    public ScreeningRunResult screen(String jobDescription, Integer shortlistCount,
                                      Double minimumShortlistScore, String requestedScoringMode,
                                      List<MultipartFile> cvFiles,
                                      ScreeningProgressListener progressListener,
                                      ScreeningPackage screeningPackage,
                                      String requestedSector) {
        return screen(jobDescription, shortlistCount, minimumShortlistScore, requestedScoringMode,
                cvFiles, progressListener, screeningPackage, requestedSector, null);
    }

    public ScreeningRunResult screen(String jobDescription, Integer shortlistCount,
                                      Double minimumShortlistScore, String requestedScoringMode,
                                      List<MultipartFile> cvFiles,
                                      ScreeningPackage screeningPackage,
                                      String requestedSector,
                                      Integer overrideAnalysisCap) {
        return screen(jobDescription, shortlistCount, minimumShortlistScore, requestedScoringMode,
                cvFiles, null, screeningPackage, requestedSector, overrideAnalysisCap);
    }

    public ScreeningRunResult screen(String jobDescription, Integer shortlistCount,
                                      Double minimumShortlistScore, String requestedScoringMode,
                                      List<MultipartFile> cvFiles,
                                      ScreeningProgressListener progressListener,
                                      String requestedSector,
                                      Integer overrideAnalysisCap) {
        return screen(jobDescription, shortlistCount, minimumShortlistScore, requestedScoringMode,
                cvFiles, progressListener, ScreeningPackage.QUICK_SCREEN, requestedSector, overrideAnalysisCap);
    }

    public ScreeningRunResult screen(String jobDescription, Integer shortlistCount,
                                      Double minimumShortlistScore, String requestedScoringMode,
                                      List<MultipartFile> cvFiles,
                                      ScreeningProgressListener progressListener,
                                      ScreeningPackage screeningPackage,
                                      String requestedSector,
                                      Integer overrideAnalysisCap) {
        return withPermit(screeningRunSemaphore(), "screening run", () -> doScreen(
                jobDescription,
                shortlistCount,
                minimumShortlistScore,
                requestedScoringMode,
                cvFiles,
                progressListener,
                screeningPackage,
                requestedSector,
                overrideAnalysisCap
        ));
    }

    private ScreeningRunResult doScreen(String jobDescription, Integer shortlistCount,
                                      Double minimumShortlistScore, String requestedScoringMode,
                                      List<MultipartFile> cvFiles,
                                      ScreeningProgressListener progressListener,
                                      ScreeningPackage screeningPackage,
                                      String requestedSector,
                                      Integer overrideAnalysisCap) {
        PipelineTimer timer = new PipelineTimer();
        TokenUsageAccumulator tokenUsageAccumulator = new TokenUsageAccumulator();
        ScreeningPackage effectivePackage = screeningPackage != null ? screeningPackage : ScreeningPackage.QUICK_SCREEN;

        timer.startPhase("resolve_settings");
        int effectiveShortlistCount = shortlistService.resolveShortlistCount(shortlistCount);
        double effectiveMinimumScore = shortlistService.resolveMinimumScore(minimumShortlistScore);
        ScoringMode effectiveMode = resolveEffectiveScoringMode(requestedScoringMode);
        Sector effectiveSector = resolveSector(requestedSector);
        int effectiveAnalysisCap = overrideAnalysisCap != null ? overrideAnalysisCap : properties.getAnalysisCap();

        timer.startPhase("job_profile_extraction");
        JobDescriptionProfile jobDescriptionProfile = jobDescriptionProfileFactory.create(jobDescription);
        jobDescriptionProfile = applyHeuristicSectorSkills(jobDescriptionProfile, effectiveSector);

        timer.startPhase("cv_text_extraction");
        emitProgress(progressListener, "extracting", 0, countNonEmptyFiles(cvFiles), null,
                "Extracting text from CVs...");
        List<DocumentExtractionOutcome> allOutcomes = cvTextExtractionService.extractAll(cvFiles);
        int totalCvsReceived = allOutcomes.size();

        timer.startPhase("deduplication");
        CvDeduplicationService.DeduplicationResult deduplicationOutcome = cvDeduplicationService.deduplicate(allOutcomes);
        List<DocumentExtractionOutcome> uniqueOutcomes = deduplicationOutcome.outcomes();
        int uniqueCvsAfterDeduplication = uniqueOutcomes.size();

        if (effectiveMode != ScoringMode.heuristic) {
            timer.startPhase("ai_job_extraction");
            emitProgress(progressListener, "scoring", 0, uniqueCvsAfterDeduplication, null,
                    "Preparing AI analysis...");
        }
        AiPreparationOutcome aiPreparationOutcome = prepareAiPrefilterContext(
                jobDescription,
                jobDescriptionProfile,
                effectiveMode,
                effectivePackage,
                tokenUsageAccumulator
        );
        effectiveMode = aiPreparationOutcome.effectiveMode();
        JobDescriptionProfile prefilterJobDescriptionProfile = aiPreparationOutcome.prefilterJobDescriptionProfile();
        AiJobDescriptionProfile aiJobProfile = aiPreparationOutcome.aiJobProfile();
        List<String> aiMustHaveSkills = aiPreparationOutcome.mustHaveSkills();

        ReductionOutcome reductionOutcome;
        if (shouldPrefilter(uniqueOutcomes, effectiveAnalysisCap)) {
            timer.startPhase("pre_filter");
            emitProgress(progressListener, "prefiltering", 0, uniqueCvsAfterDeduplication, null,
                    "Pre-filtering candidates...");
            reductionOutcome = reduceToAnalysisCap(uniqueOutcomes, prefilterJobDescriptionProfile, aiMustHaveSkills, effectiveAnalysisCap);
        } else {
            reductionOutcome = new ReductionOutcome(uniqueOutcomes, List.of());
        }

        List<DocumentExtractionOutcome> outcomesForAnalysis = reductionOutcome.outcomesForAnalysis();
        int candidatesScored = outcomesForAnalysis.size();

        if (uniqueCvsAfterDeduplication > candidatesScored) {
            log.info("First-pass reduction: {} CVs considered after deduplication, reduced to top {} for full analysis",
                    uniqueCvsAfterDeduplication, candidatesScored);
        }

        timer.startPhase("candidate_scoring");
        AnalysisOutcome analysisOutcome = analyseCandidates(
                jobDescriptionProfile,
                aiJobProfile,
                effectiveMode,
                effectiveSector,
                effectivePackage,
                outcomesForAnalysis,
                progressListener,
                tokenUsageAccumulator
        );
        List<CandidateEvaluation> evaluations = analysisOutcome.evaluations();
        List<CandidateEvaluation> zeroScoreEvaluations = evaluations.stream()
                .filter(evaluation -> evaluation.score() <= 0.0)
                .toList();
        List<CandidateEvaluation> viableEvaluations = evaluations.stream()
                .filter(evaluation -> evaluation.score() > 0.0)
                .toList();
        if (!zeroScoreEvaluations.isEmpty()) {
            log.info("Excluding {} zero-score candidate(s) from final results after full analysis",
                    zeroScoreEvaluations.size());
        }

        if (analysisOutcome.anyAiFallback() && effectiveMode == ScoringMode.ai) {
            effectiveMode = ScoringMode.ai_with_fallbacks;
        }

        timer.startPhase("ranking_and_shortlisting");
        emitProgress(progressListener, "finalising", candidatesScored, candidatesScored, null,
                "Ranking and shortlisting candidates...");
        ScreeningResult screeningResult = new ScreeningResult(
                jobDescriptionProfile,
                shortlistService.shortlist(rankingService.rank(viableEvaluations), effectiveShortlistCount, effectiveMinimumScore)
        );

        String aiJobProfileJson = aiJobProfile != null ? safeSerialize(aiJobProfile) : null;
        String promptVersions = effectiveMode != ScoringMode.heuristic
                ? com.recruiter.ai.AiPromptVersions.JOB_SKILL_EXTRACTOR + ","
                + com.recruiter.ai.AiPromptVersions.JOB_EXTRACTOR + ","
                + com.recruiter.ai.AiPromptVersions.CANDIDATE_EXTRACTOR + ","
                + com.recruiter.ai.AiPromptVersions.FIT_ASSESSOR
                : null;

        TokenUsage tokenUsage = tokenUsageAccumulator.toTokenUsage();
        Double estimatedCostUsd = tokenUsage.totalTokens() > 0
                ? tokenUsage.estimatedCostUsd(
                properties.getAiCost().getPromptPricePerMillion(),
                properties.getAiCost().getCompletionPricePerMillion())
                : null;

        List<EliminatedCandidateSnapshot> eliminatedCandidates = new ArrayList<>(reductionOutcome.eliminatedCandidates());
        zeroScoreEvaluations.stream()
                .map(this::toZeroScoreEliminatedCandidate)
                .forEach(eliminatedCandidates::add);

        timer.startPhase("persistence");
        Long batchId = screeningBatchPersistenceService.save(
                jobDescription, effectiveShortlistCount, effectiveMode, effectivePackage.name(), effectiveSector.name(),
                totalCvsReceived, candidatesScored, effectiveMinimumScore,
                tokenUsage, estimatedCostUsd, null,
                aiJobProfileJson, promptVersions, screeningResult, eliminatedCandidates);
        timer.endCurrentPhase();
        long processingTimeMs = timer.totalElapsed();
        screeningBatchPersistenceService.updateProcessingTime(batchId, processingTimeMs);
        logPipelineSummary(batchId, timer, tokenUsage, estimatedCostUsd,
                totalCvsReceived, uniqueCvsAfterDeduplication, candidatesScored, effectiveMode, effectivePackage);
        log.info("Screening request persisted: batchId={}, mode={}", batchId, effectiveMode);
        return new ScreeningRunResult(batchId, effectiveShortlistCount, effectiveMode,
                effectiveSector,
                totalCvsReceived, deduplicationOutcome.exactDuplicatesRemoved(),
                deduplicationOutcome.nearDuplicatesRemoved(), deduplicationOutcome.duplicatesRemoved(), candidatesScored,
                tokenUsage, estimatedCostUsd, processingTimeMs, screeningResult);
    }

    private AnalysisOutcome analyseCandidates(JobDescriptionProfile jobDescriptionProfile,
                                              AiJobDescriptionProfile aiJobProfile,
                                              ScoringMode effectiveMode,
                                              Sector sector,
                                              ScreeningPackage screeningPackage,
                                              List<DocumentExtractionOutcome> outcomesForAnalysis,
                                              ScreeningProgressListener progressListener,
                                              TokenUsageAccumulator tokenUsageAccumulator) {
        if (effectiveMode != ScoringMode.heuristic && aiJobProfile != null) {
            return analyseCandidatesWithAi(jobDescriptionProfile, aiJobProfile, sector, screeningPackage,
                    outcomesForAnalysis, progressListener, tokenUsageAccumulator);
        }
        return new AnalysisOutcome(
                analyseCandidatesSequentially(jobDescriptionProfile, sector, outcomesForAnalysis, progressListener),
                false
        );
    }

    private AnalysisOutcome analyseCandidatesWithAi(JobDescriptionProfile jobDescriptionProfile,
                                                    AiJobDescriptionProfile aiJobProfile,
                                                    Sector sector,
                                                    ScreeningPackage screeningPackage,
                                                    List<DocumentExtractionOutcome> outcomesForAnalysis,
                                                    ScreeningProgressListener progressListener,
                                                    TokenUsageAccumulator tokenUsageAccumulator) {
        AtomicBoolean anyAiFallback = new AtomicBoolean(false);
        AtomicInteger completed = new AtomicInteger(0);
        int total = outcomesForAnalysis.size();
        String sectorSystemPrompt = promptProviderFactory.getProvider(sector).getSystemPrompt();
        log.info("AI screening using sector prompt: sector={}", sector);
        log.info("AI screening model selected: package={}, model={}, step=candidate_fit_assessment, candidates={}",
                screeningPackage, aiModelSelectionService.screeningModel(screeningPackage), total);

        List<CompletableFuture<CandidateEvaluation>> futures = outcomesForAnalysis.stream()
                .map(outcome -> CompletableFuture.supplyAsync(() ->
                                evaluateCandidateWithAiFallback(jobDescriptionProfile, aiJobProfile, sector,
                                        screeningPackage, sectorSystemPrompt, outcome, anyAiFallback, tokenUsageAccumulator),
                        screeningVirtualExecutor
                ).thenApply(evaluation -> {
                    int count = completed.incrementAndGet();
                    emitProgress(progressListener, "scoring", count, total,
                            evaluation.candidateProfile().candidateName(),
                            "Analysing candidate " + count + " of " + total + "...");
                    return evaluation;
                }))
                .toList();

        List<CandidateEvaluation> evaluations = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        return new AnalysisOutcome(evaluations, anyAiFallback.get());
    }

    private List<CandidateEvaluation> analyseCandidatesSequentially(JobDescriptionProfile jobDescriptionProfile,
                                                                    Sector sector,
                                                                    List<DocumentExtractionOutcome> outcomesForAnalysis,
                                                                    ScreeningProgressListener progressListener) {
        List<CandidateEvaluation> evaluations = new ArrayList<>(outcomesForAnalysis.size());
        int total = outcomesForAnalysis.size();
        int completed = 0;
        List<String> sectorSkills = SectorSkillDictionary.getSkills(sector);

        for (DocumentExtractionOutcome extractionOutcome : outcomesForAnalysis) {
            CandidateEvaluation evaluation = evaluateHeuristically(jobDescriptionProfile, sectorSkills, extractionOutcome);
            evaluations.add(evaluation);
            completed++;
            emitProgress(progressListener, "scoring", completed, total,
                    evaluation.candidateProfile().candidateName(),
                    "Analysing candidate " + completed + " of " + total + "...");
        }
        return evaluations;
    }

    private ReductionOutcome reduceToAnalysisCap(List<DocumentExtractionOutcome> allOutcomes,
                                                 JobDescriptionProfile jobDescriptionProfile,
                                                 List<String> aiMustHaveSkills,
                                                 int analysisCap) {

        List<DocumentExtractionOutcome> readable = allOutcomes.stream()
                .filter(DocumentExtractionOutcome::succeeded)
                .toList();
        List<DocumentExtractionOutcome> failed = allOutcomes.stream()
                .filter(outcome -> !outcome.succeeded())
                .toList();

        if (readable.size() <= analysisCap) {
            return new ReductionOutcome(allOutcomes, List.of());
        }

        List<ScoredOutcome> sorted = scoreAllReadable(readable, jobDescriptionProfile, aiMustHaveSkills);
        List<ScoredOutcome> guaranteed = selectGuaranteed(sorted, analysisCap);

        double cutoffScore = guaranteed.get(guaranteed.size() - 1).score();
        double margin = properties.getPrefilterBorderlineMargin();
        int maxRescue = properties.getPrefilterMaxRescue();
        List<ScoredOutcome> rescued = selectRescueCandidates(sorted, analysisCap, cutoffScore, margin, maxRescue);

        return buildReductionOutcome(guaranteed, rescued, sorted, analysisCap, failed, cutoffScore, margin);
    }

    private List<ScoredOutcome> scoreAllReadable(List<DocumentExtractionOutcome> readable,
                                                 JobDescriptionProfile jobDescriptionProfile,
                                                 List<String> aiMustHaveSkills) {
        int totalJobSkills = jobDescriptionProfile.extractedSkills().size();
        return readable.stream()
                .map(outcome -> {
                    CandidateProfile profile = buildPrefilterCandidateProfile(outcome.extractedDocument(), jobDescriptionProfile);
                    List<String> matched = matchedSkills(jobDescriptionProfile, profile);
                    double prefilterScore = candidateScoringService.scoreForPrefilter(
                            jobDescriptionProfile,
                            profile,
                            aiMustHaveSkills
                    );
                    return new ScoredOutcome(
                            outcome,
                            prefilterScore,
                            profile.candidateName(),
                            profile.sourceFilename(),
                            matched,
                            matched.size(),
                            totalJobSkills,
                            profile.yearsOfExperience() != null
                    );
                })
                .sorted(Comparator.comparingDouble(ScoredOutcome::score).reversed())
                .toList();
    }

    private List<ScoredOutcome> selectGuaranteed(List<ScoredOutcome> sorted, int analysisCap) {
        return sorted.subList(0, Math.min(analysisCap, sorted.size()));
    }

    private List<ScoredOutcome> selectRescueCandidates(List<ScoredOutcome> sorted, int analysisCap,
                                                        double cutoffScore, double margin, int maxRescue) {
        if (margin <= 0 || analysisCap >= sorted.size()) {
            return List.of();
        }

        double rescueFloor = Math.max(cutoffScore - margin, 0.0);
        List<ScoredOutcome> rescued = new ArrayList<>();

        for (int i = analysisCap; i < sorted.size() && rescued.size() < maxRescue; i++) {
            ScoredOutcome candidate = sorted.get(i);

            if (candidate.score() >= rescueFloor) {
                rescued.add(candidate);
                log.info("Pre-filter rescue (margin): '{}' score={} (floor={})",
                        candidate.candidateName(), candidate.score(), rescueFloor);
            } else if (candidate.totalJobSkillCount() > 0
                    && candidate.matchedSkillCount() >= candidate.totalJobSkillCount() / 2.0
                    && candidate.hasExperienceEvidence()) {
                rescued.add(candidate);
                log.info("Pre-filter rescue (skill+experience): '{}' score={}, skills={}/{}, hasExperience=true",
                        candidate.candidateName(), candidate.score(),
                        candidate.matchedSkillCount(), candidate.totalJobSkillCount());
            }
        }

        return rescued;
    }

    private ReductionOutcome buildReductionOutcome(List<ScoredOutcome> guaranteed,
                                                    List<ScoredOutcome> rescued,
                                                    List<ScoredOutcome> allSorted,
                                                    int analysisCap,
                                                    List<DocumentExtractionOutcome> failed,
                                                    double cutoffScore,
                                                    double margin) {
        java.util.Set<DocumentExtractionOutcome> selectedOutcomes = new java.util.LinkedHashSet<>();
        for (ScoredOutcome g : guaranteed) {
            selectedOutcomes.add(g.outcome());
        }
        for (ScoredOutcome r : rescued) {
            selectedOutcomes.add(r.outcome());
        }

        List<EliminatedCandidateSnapshot> eliminated = allSorted.stream()
                .skip(analysisCap)
                .filter(s -> !selectedOutcomes.contains(s.outcome()))
                .map(ScoredOutcome::toEliminatedCandidate)
                .toList();

        double rescueFloor = Math.max(cutoffScore - margin, 0.0);
        log.info("Pre-filter: {} guaranteed, {} rescued (margin={}, floor={}), {} eliminated",
                guaranteed.size(), rescued.size(), margin, rescueFloor, eliminated.size());

        if (!failed.isEmpty()) {
            log.warn("Excluding {} unreadable CV(s) from full analysis because readable candidates exceeded the analysis cap",
                    failed.size());
        }

        List<DocumentExtractionOutcome> result = new ArrayList<>(selectedOutcomes.size());
        result.addAll(selectedOutcomes);
        return new ReductionOutcome(result, eliminated);
    }

    private boolean shouldPrefilter(List<DocumentExtractionOutcome> outcomes, int analysisCap) {
        long readableCount = outcomes.stream()
                .filter(DocumentExtractionOutcome::succeeded)
                .count();
        return readableCount > analysisCap;
    }

    private AiPreparationOutcome prepareAiPrefilterContext(String jobDescriptionText,
                                                           JobDescriptionProfile baseProfile,
                                                           ScoringMode scoringMode,
                                                           ScreeningPackage screeningPackage,
                                                           TokenUsageAccumulator tokenUsageAccumulator) {
        if (scoringMode != ScoringMode.ai) {
            return new AiPreparationOutcome(baseProfile, null, List.of(), scoringMode);
        }

        try {
            AiResult<AiJobDescriptionProfile> aiJobResult = jobDescriptionAiExtractor.orElseThrow()
                    .extract(jobDescriptionText, screeningPackage);
            tokenUsageAccumulator.add(aiJobResult.tokenUsage());
            AiJobDescriptionProfile aiJobProfile = aiJobResult.result();
            log.info("AI job description extraction succeeded: roleTitle='{}'", aiJobProfile.roleTitle());

            JobDescriptionProfile prefilterJobProfile = buildAiEnhancedPrefilterJobDescriptionProfile(
                    jobDescriptionText,
                    baseProfile,
                    screeningPackage,
                    tokenUsageAccumulator
            );
            List<String> mustHaveSkills = extractMustHaveSkills(aiJobProfile, prefilterJobProfile);
            return new AiPreparationOutcome(prefilterJobProfile, aiJobProfile, mustHaveSkills, scoringMode);
        } catch (Exception ex) {
            log.warn("AI job extraction failed; pre-filter using heuristic scoring only. {}", ex.getMessage());
            return new AiPreparationOutcome(baseProfile, null, List.of(), ScoringMode.heuristic);
        }
    }

    private JobDescriptionProfile buildAiEnhancedPrefilterJobDescriptionProfile(String jobDescriptionText,
                                                                                JobDescriptionProfile baseProfile,
                                                                                ScreeningPackage screeningPackage,
                                                                                TokenUsageAccumulator tokenUsageAccumulator) {
        if (aiSkillExtractor.isEmpty()) {
            return baseProfile;
        }

        try {
            AiResult<ExtractedJobSkills> skillExtractionResult = aiSkillExtractor.orElseThrow()
                    .extract(jobDescriptionText, screeningPackage);
            tokenUsageAccumulator.add(skillExtractionResult.tokenUsage());
            List<String> mergedSkills = mergeSkills(baseProfile.extractedSkills(), skillExtractionResult.result().skills());
            if (mergedSkills.equals(baseProfile.extractedSkills())) {
                return baseProfile;
            }

            log.info("AI pre-filter skill extraction added {} domain-specific terms",
                    mergedSkills.size() - baseProfile.extractedSkills().size());
            return new JobDescriptionProfile(
                    baseProfile.originalText(),
                    mergedSkills,
                    baseProfile.requiredKeywords(),
                    baseProfile.yearsOfExperience()
            );
        } catch (Exception ex) {
            log.warn("AI pre-filter skill extraction failed, using static heuristic skills only: {}", ex.getMessage());
            return baseProfile;
        }
    }

    private Sector resolveSector(String requested) {
        if (requested != null && !requested.isBlank()) {
            return Sector.fromString(requested);
        }
        return properties.getEffectiveSector();
    }

    private ScoringMode resolveEffectiveScoringMode(String requested) {
        if ("heuristic".equalsIgnoreCase(requested)) {
            return ScoringMode.heuristic;
        }
        boolean aiAvailable = jobDescriptionAiExtractor.isPresent()
                && candidateAiExtractor.isPresent()
                && fitAssessmentAiService.isPresent();
        if (!aiAvailable) {
            log.warn("AI mode requested but AI services are not configured. Falling back to heuristic mode.");
            return ScoringMode.heuristic;
        }
        return ScoringMode.ai;
    }

    private CandidateEvaluation tryAiEvaluation(AiJobDescriptionProfile aiJobProfile,
                                                Sector sector,
                                                ScreeningPackage screeningPackage,
                                                String sectorSystemPrompt,
                                                DocumentExtractionOutcome extractionOutcome,
                                                TokenUsageAccumulator tokenUsageAccumulator) {
        try {
            String cvText = extractionOutcome.extractedDocument().text();
            CandidateProfile candidateProfile = candidateProfileFactory.create(extractionOutcome.extractedDocument());
            long totalStartedAt = System.currentTimeMillis();

            long extractStartedAt = System.currentTimeMillis();
            AiResult<AiCandidateProfile> aiCandidateResult = candidateAiExtractor.orElseThrow()
                    .extract(cvText, screeningPackage);
            long extractDurationMs = System.currentTimeMillis() - extractStartedAt;
            tokenUsageAccumulator.add(aiCandidateResult.tokenUsage());

            long assessStartedAt = System.currentTimeMillis();
            AiResult<AiFitAssessment> fitAssessmentResult = fitAssessmentAiService.orElseThrow()
                    .assess(aiJobProfile, aiCandidateResult.result(), sectorSystemPrompt, screeningPackage);
            long assessDurationMs = System.currentTimeMillis() - assessStartedAt;
            tokenUsageAccumulator.add(fitAssessmentResult.tokenUsage());

            CandidateEvaluation evaluation = aiMapper.map(candidateProfile, fitAssessmentResult.result(), sector);
            long totalDurationMs = System.currentTimeMillis() - totalStartedAt;
            log.info("AI scoring for '{}': extract={}ms, assess={}ms, total={}ms",
                    candidateProfile.candidateName(),
                    extractDurationMs,
                    assessDurationMs,
                    totalDurationMs);
            return evaluation;
        } catch (Exception ex) {
            log.warn("AI evaluation failed for '{}': {}", extractionOutcome.originalFilename(), ex.getMessage());
            return null;
        }
    }

    private CandidateEvaluation evaluateCandidateWithAiFallback(JobDescriptionProfile jobDescriptionProfile,
                                                                AiJobDescriptionProfile aiJobProfile,
                                                                Sector sector,
                                                                ScreeningPackage screeningPackage,
                                                                String sectorSystemPrompt,
                                                                DocumentExtractionOutcome extractionOutcome,
                                                                AtomicBoolean anyAiFallback,
                                                                TokenUsageAccumulator tokenUsageAccumulator) {
        log.info("Candidate processing started: filename='{}', mode={}", extractionOutcome.originalFilename(), ScoringMode.ai);
        long startedAt = System.currentTimeMillis();

        if (!extractionOutcome.succeeded()) {
            return buildExtractionFailureEvaluation(extractionOutcome);
        }

        CandidateEvaluation aiEval = withPermit(aiCandidateEvaluationSemaphore(), "AI candidate evaluation",
                () -> tryAiEvaluation(aiJobProfile, sector, screeningPackage,
                        sectorSystemPrompt, extractionOutcome, tokenUsageAccumulator));
        if (aiEval != null) {
            log.info("AI candidate processing finished: filename='{}', score={}",
                    extractionOutcome.originalFilename(), aiEval.score());
            return aiEval;
        }

        anyAiFallback.set(true);
        log.warn("AI scoring failed for '{}' after {}ms, falling back to heuristic",
                extractionOutcome.originalFilename(),
                System.currentTimeMillis() - startedAt);
        CandidateEvaluation fallbackEval = markHeuristicFallback(
                buildHeuristicEvaluation(jobDescriptionProfile, List.of(), extractionOutcome)
        );
        log.info("Heuristic fallback candidate processing finished: filename='{}', score={}",
                extractionOutcome.originalFilename(), fallbackEval.score());
        return fallbackEval;
    }

    private <T> T withPermit(Semaphore semaphore, String operation, java.util.function.Supplier<T> supplier) {
        boolean acquired = false;
        try {
            semaphore.acquire();
            acquired = true;
            return supplier.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for " + operation + " capacity", ex);
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }

    private Semaphore screeningRunSemaphore() {
        Semaphore semaphore = screeningRunSemaphore;
        if (semaphore == null) {
            synchronized (this) {
                semaphore = screeningRunSemaphore;
                if (semaphore == null) {
                    semaphore = new Semaphore(properties.getConcurrency().getMaxConcurrentScreeningRuns(), true);
                    screeningRunSemaphore = semaphore;
                }
            }
        }
        return semaphore;
    }

    private Semaphore aiCandidateEvaluationSemaphore() {
        Semaphore semaphore = aiCandidateEvaluationSemaphore;
        if (semaphore == null) {
            synchronized (this) {
                semaphore = aiCandidateEvaluationSemaphore;
                if (semaphore == null) {
                    semaphore = new Semaphore(properties.getConcurrency().getMaxConcurrentAiCandidateEvaluations(), true);
                    aiCandidateEvaluationSemaphore = semaphore;
                }
            }
        }
        return semaphore;
    }

    private CandidateEvaluation evaluateHeuristically(JobDescriptionProfile jobDescriptionProfile,
                                                      List<String> sectorSkills,
                                                      DocumentExtractionOutcome extractionOutcome) {
        log.info("Candidate processing started: filename='{}', mode={}",
                extractionOutcome.originalFilename(), ScoringMode.heuristic);
        if (!extractionOutcome.succeeded()) {
            return buildExtractionFailureEvaluation(extractionOutcome);
        }

        CandidateEvaluation heuristicEval = buildHeuristicEvaluation(jobDescriptionProfile, sectorSkills, extractionOutcome);
        log.info("Heuristic candidate processing finished: filename='{}', score={}",
                extractionOutcome.originalFilename(), heuristicEval.score());
        return heuristicEval;
    }

    private CandidateEvaluation buildHeuristicEvaluation(JobDescriptionProfile jobDescriptionProfile,
                                                          List<String> sectorSkills,
                                                          DocumentExtractionOutcome extractionOutcome) {
        CandidateProfile candidateProfile = buildSectorAwareCandidateProfile(
                extractionOutcome.extractedDocument(), sectorSkills);
        return candidateScoringService.evaluate(jobDescriptionProfile, candidateProfile);
    }

    /**
     * Merges sector-specific skill terms (from {@link SectorSkillDictionary}) into the job profile's
     * extracted skills. Terms are only added if they actually appear in the job description text,
     * so noise from irrelevant sector terms is minimised. Returns the original profile unchanged
     * when the sector is GENERIC or no new terms are found.
     */
    private JobDescriptionProfile applyHeuristicSectorSkills(JobDescriptionProfile baseProfile, Sector sector) {
        List<String> sectorSkills = SectorSkillDictionary.getSkills(sector);
        if (sectorSkills.isEmpty()) {
            return baseProfile;
        }
        List<String> mergedSkills = heuristicsService.extractSkills(baseProfile.originalText(), sectorSkills);
        if (mergedSkills.equals(baseProfile.extractedSkills())) {
            return baseProfile;
        }
        log.info("Heuristic sector skill boost ({}): {} -> {} skills",
                sector, baseProfile.extractedSkills().size(), mergedSkills.size());
        return new JobDescriptionProfile(
                baseProfile.originalText(),
                mergedSkills,
                baseProfile.requiredKeywords(),
                baseProfile.yearsOfExperience()
        );
    }

    /**
     * Builds a candidate profile where sector-specific skill terms are checked against
     * the CV text in addition to the generic skill dictionary. This ensures that sector
     * vocabulary found in the JD (e.g. "NMC", "SMSTS") can also be matched in CVs.
     */
    private CandidateProfile buildSectorAwareCandidateProfile(ExtractedDocument extractedDocument,
                                                               List<String> sectorSkills) {
        CandidateProfile baseProfile = candidateProfileFactory.create(extractedDocument);
        if (sectorSkills.isEmpty()) {
            return baseProfile;
        }
        List<String> enrichedSkills = heuristicsService.extractSkills(baseProfile.extractedText(), sectorSkills);
        if (enrichedSkills.equals(baseProfile.extractedSkills())) {
            return baseProfile;
        }
        return new CandidateProfile(
                baseProfile.candidateName(),
                baseProfile.sourceFilename(),
                baseProfile.extractedText(),
                enrichedSkills,
                baseProfile.yearsOfExperience()
        );
    }

    private CandidateProfile buildPrefilterCandidateProfile(ExtractedDocument extractedDocument,
                                                            JobDescriptionProfile jobDescriptionProfile) {
        CandidateProfile baseProfile = candidateProfileFactory.create(extractedDocument);
        List<String> extractedSkills = heuristicsService.extractSkills(
                baseProfile.extractedText(),
                jobDescriptionProfile.extractedSkills()
        );
        if (extractedSkills.equals(baseProfile.extractedSkills())) {
            return baseProfile;
        }
        return new CandidateProfile(
                baseProfile.candidateName(),
                baseProfile.sourceFilename(),
                baseProfile.extractedText(),
                extractedSkills,
                baseProfile.yearsOfExperience()
        );
    }

    private CandidateEvaluation markHeuristicFallback(CandidateEvaluation evaluation) {
        return new CandidateEvaluation(
                evaluation.candidateProfile(),
                evaluation.score(),
                evaluation.scoreBreakdown(),
                "heuristic_fallback",
                evaluation.summary(),
                evaluation.shortlisted(),
                evaluation.aiConfidence(),
                evaluation.aiTopStrengths(),
                evaluation.aiTopGaps(),
                evaluation.aiInterviewProbeAreas()
        );
    }

    private CandidateEvaluation buildExtractionFailureEvaluation(DocumentExtractionOutcome extractionOutcome) {
        log.warn("Candidate processing failed: filename='{}', reason='{}'",
                extractionOutcome.originalFilename(), extractionOutcome.failureMessage());
        CandidateProfile failedProfile = candidateProfileFactory.create(
                new ExtractedDocument(extractionOutcome.originalFilename(), "")
        );
        return new CandidateEvaluation(
                failedProfile,
                0.0,
                "CV extraction failed for '" + extractionOutcome.originalFilename() + "'. "
                        + extractionOutcome.failureMessage(),
                false
        );
    }

    private String safeSerialize(Object value) {
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize AI output for audit: {}", ex.getMessage());
            return null;
        }
    }

    private int countNonEmptyFiles(List<MultipartFile> cvFiles) {
        if (cvFiles == null || cvFiles.isEmpty()) {
            return 0;
        }
        return (int) cvFiles.stream()
                .filter(file -> file != null && !file.isEmpty())
                .count();
    }

    private void emitProgress(ScreeningProgressListener progressListener,
                              String phase,
                              int completed,
                              int total,
                              String candidateName,
                              String message) {
        if (progressListener == null) {
            return;
        }
        progressListener.onProgress(new ScreeningProgressEvent(
                phase,
                completed,
                total,
                candidateName,
                message
        ));
    }

    private List<String> matchedSkills(JobDescriptionProfile jobDescriptionProfile, CandidateProfile candidateProfile) {
        if (jobDescriptionProfile.extractedSkills().isEmpty() || candidateProfile.extractedSkills().isEmpty()) {
            return List.of();
        }

        java.util.Set<String> jobSkills = jobDescriptionProfile.extractedSkills().stream()
                .map(skill -> skill.toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());

        return candidateProfile.extractedSkills().stream()
                .filter(skill -> jobSkills.contains(skill.toLowerCase(java.util.Locale.ROOT)))
                .distinct()
                .toList();
    }

    private EliminatedCandidateSnapshot toZeroScoreEliminatedCandidate(CandidateEvaluation evaluation) {
        return new EliminatedCandidateSnapshot(
                evaluation.candidateProfile().candidateName(),
                evaluation.candidateProfile().sourceFilename(),
                evaluation.score(),
                evaluation.candidateProfile().extractedSkills(),
                "Final score",
                "Excluded after full analysis because the final score was 0."
        );
    }

    private List<String> mergeSkills(List<String> baseSkills, List<String> additionalSkills) {
        LinkedHashMap<String, String> merged = new LinkedHashMap<>();
        for (String skill : baseSkills) {
            addMergedSkill(merged, skill);
        }
        for (String skill : additionalSkills) {
            addMergedSkill(merged, skill);
        }
        return List.copyOf(merged.values());
    }

    private List<String> extractMustHaveSkills(AiJobDescriptionProfile aiJobProfile,
                                               JobDescriptionProfile jobDescriptionProfile) {
        if (aiJobProfile == null) {
            return List.of();
        }

        List<String> mustHaveRequirementTexts = new ArrayList<>();
        collectMustHaveRequirementTexts(mustHaveRequirementTexts, aiJobProfile.essentialRequirements());
        collectMustHaveRequirementTexts(mustHaveRequirementTexts, aiJobProfile.desirableRequirements());
        if (mustHaveRequirementTexts.isEmpty()) {
            return List.of();
        }

        String combinedRequirements = String.join(". ", mustHaveRequirementTexts);
        return heuristicsService.extractSkills(combinedRequirements, jobDescriptionProfile.extractedSkills());
    }

    private void collectMustHaveRequirementTexts(List<String> mustHaveRequirementTexts,
                                                 Collection<com.recruiter.ai.RequirementItem> requirements) {
        if (requirements == null) {
            return;
        }
        for (com.recruiter.ai.RequirementItem requirement : requirements) {
            if (requirement == null || requirement.requirement() == null || requirement.requirement().isBlank()) {
                continue;
            }
            if (requirement.importance() != com.recruiter.ai.ImportanceLevel.MUST_HAVE) {
                continue;
            }
            if (requirement.type() != com.recruiter.ai.RequirementType.SKILL
                    && requirement.type() != com.recruiter.ai.RequirementType.TOOL_OR_SYSTEM) {
                continue;
            }
            mustHaveRequirementTexts.add(requirement.requirement());
        }
    }

    private void addMergedSkill(LinkedHashMap<String, String> merged, String skill) {
        if (skill == null || skill.isBlank()) {
            return;
        }
        merged.putIfAbsent(skill.trim().toLowerCase(java.util.Locale.ROOT), skill.trim());
    }

    private void logPipelineSummary(Long batchId,
                                    PipelineTimer timer,
                                    TokenUsage tokenUsage,
                                    Double estimatedCostUsd,
                                    int totalCvsReceived,
                                    int uniqueCvsAfterDeduplication,
                                    int candidatesScored,
                                    ScoringMode effectiveMode,
                                    ScreeningPackage screeningPackage) {
        Map<String, Long> phaseDurations = timer.getPhaseDurations();
        log.info("======================================================");
        log.info("Screening pipeline complete - Batch #{}", batchId);
        log.info("------------------------------------------------------");
        logPhase(phaseDurations, "resolve_settings", "Settings resolution", null);
        logPhase(phaseDurations, "job_profile_extraction", "Job profile extraction", null);
        logPhase(phaseDurations, "cv_text_extraction", "CV text extraction",
                " (" + totalCvsReceived + " files)");
        logPhase(phaseDurations, "deduplication", "Deduplication", null);
        if (phaseDurations.containsKey("pre_filter")) {
            logPhase(phaseDurations, "pre_filter", "Pre-filter",
                    " (" + uniqueCvsAfterDeduplication + " -> " + candidatesScored + " candidates)");
        }
        if (phaseDurations.containsKey("ai_job_extraction")) {
            logPhase(phaseDurations, "ai_job_extraction", "AI job extraction", null);
        }
        logPhase(phaseDurations, "candidate_scoring", "Candidate scoring",
                " (" + candidatesScored + " candidates, " + (effectiveMode == ScoringMode.heuristic ? "sequential" : "parallel") + ")");
        logPhase(phaseDurations, "ranking_and_shortlisting", "Ranking & shortlisting", null);
        logPhase(phaseDurations, "persistence", "Persistence", null);
        log.info("------------------------------------------------------");
        log.info("  Total wall time:         {}", formatSummaryDuration(timer.totalElapsed()));
        if (tokenUsage.totalTokens() > 0) {
            log.info("  AI tokens used:          {} (prompt: {}, completion: {}); AI model used: {}",
                    String.format(Locale.US, "%,d", tokenUsage.totalTokens()),
                    String.format(Locale.US, "%,d", tokenUsage.promptTokens()),
                    String.format(Locale.US, "%,d", tokenUsage.completionTokens()),
                    aiModelSelectionService.screeningModel(screeningPackage));
            log.info("  Estimated cost:          ${}",
                    String.format(Locale.US, "%.4f", estimatedCostUsd != null ? estimatedCostUsd : 0.0));
        }
        log.info("======================================================");
    }

    private void logPhase(Map<String, Long> phaseDurations, String key, String label, String suffix) {
        Long durationMs = phaseDurations.get(key);
        if (durationMs == null) {
            return;
        }
        log.info("  {} {}", padRight(label + ":", 24), formatSummaryDuration(durationMs) + (suffix != null ? suffix : ""));
    }

    private String padRight(String value, int width) {
        return String.format(Locale.US, "%-" + width + "s", value);
    }

    private String formatSummaryDuration(long durationMs) {
        return String.format(Locale.US, "%,dms", durationMs);
    }

    private record ScoredOutcome(DocumentExtractionOutcome outcome,
                                 double score,
                                 String candidateName,
                                 String candidateFilename,
                                 List<String> matchedSkills,
                                 int matchedSkillCount,
                                 int totalJobSkillCount,
                                 boolean hasExperienceEvidence) {

        private EliminatedCandidateSnapshot toEliminatedCandidate() {
            return new EliminatedCandidateSnapshot(
                    candidateName,
                    candidateFilename,
                    score,
                    matchedSkills,
                    "First-pass score",
                    "Removed during the first-pass relevance filter before full analysis."
            );
        }
    }

    private record AnalysisOutcome(List<CandidateEvaluation> evaluations, boolean anyAiFallback) {
    }

    private record ReductionOutcome(List<DocumentExtractionOutcome> outcomesForAnalysis,
                                    List<EliminatedCandidateSnapshot> eliminatedCandidates) {
    }

    private record AiPreparationOutcome(JobDescriptionProfile prefilterJobDescriptionProfile,
                                        AiJobDescriptionProfile aiJobProfile,
                                        List<String> mustHaveSkills,
                                        ScoringMode effectiveMode) {
    }
}

// Future orchestration hook: an agent framework such as Embabel could wrap
// job extraction, candidate extraction, fit assessment, and ranking
// as coordinated agent actions. The current sequential flow in screen()
// maps directly to such an orchestration pattern.
