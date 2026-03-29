package com.recruiter.service;

import com.recruiter.ai.AiAssessmentToCandidateEvaluationMapper;
import com.recruiter.ai.AiCandidateProfile;
import com.recruiter.ai.AiFitAssessment;
import com.recruiter.ai.AiJobDescriptionProfile;
import com.recruiter.ai.CandidateAiExtractor;
import com.recruiter.ai.FitAssessmentAiService;
import com.recruiter.ai.JobDescriptionAiExtractor;
import com.recruiter.config.RecruitmentProperties;
import com.recruiter.document.CvTextExtractionService;
import com.recruiter.document.DocumentExtractionOutcome;
import com.recruiter.document.ExtractedDocument;
import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.CandidateProfile;
import com.recruiter.domain.JobDescriptionProfile;
import com.recruiter.domain.ScoringMode;
import com.recruiter.domain.ScreeningRunResult;
import com.recruiter.domain.ScreeningResult;
import com.recruiter.persistence.ScreeningBatchPersistenceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
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
    private final RankingService rankingService;
    private final ShortlistService shortlistService;
    private final ScreeningBatchPersistenceService screeningBatchPersistenceService;
    private final RecruitmentProperties properties;
    private final Optional<JobDescriptionAiExtractor> jobDescriptionAiExtractor;
    private final Optional<CandidateAiExtractor> candidateAiExtractor;
    private final Optional<FitAssessmentAiService> fitAssessmentAiService;
    private final AiAssessmentToCandidateEvaluationMapper aiMapper;
    private final ExecutorService screeningVirtualExecutor;

    public ScreeningRunResult screen(String jobDescription, Integer shortlistCount,
                                      Double minimumShortlistScore, String requestedScoringMode,
                                      List<MultipartFile> cvFiles) {
        return screen(jobDescription, shortlistCount, minimumShortlistScore, requestedScoringMode, cvFiles, null);
    }

    public ScreeningRunResult screen(String jobDescription, Integer shortlistCount,
                                      Double minimumShortlistScore, String requestedScoringMode,
                                      List<MultipartFile> cvFiles,
                                      ScreeningProgressListener progressListener) {
        int effectiveShortlistCount = shortlistService.resolveShortlistCount(shortlistCount);
        double effectiveMinimumScore = shortlistService.resolveMinimumScore(minimumShortlistScore);
        ScoringMode effectiveMode = resolveEffectiveScoringMode(requestedScoringMode);

        JobDescriptionProfile jobDescriptionProfile = jobDescriptionProfileFactory.create(jobDescription);
        emitProgress(progressListener, "extracting", 0, countNonEmptyFiles(cvFiles), null,
                "Extracting text from CVs...");
        List<DocumentExtractionOutcome> allOutcomes = cvTextExtractionService.extractAll(cvFiles);
        int totalCvsReceived = allOutcomes.size();

        emitProgress(progressListener, "prefiltering", 0, totalCvsReceived, null,
                "Pre-filtering candidates...");
        // First-pass reduction: if more readable CVs than analysis-cap, keep only the top N
        List<DocumentExtractionOutcome> outcomesForAnalysis = reduceToAnalysisCap(allOutcomes, jobDescriptionProfile);
        int candidatesScored = outcomesForAnalysis.size();

        if (totalCvsReceived > candidatesScored) {
            log.info("First-pass reduction: {} CVs received, reduced to top {} for full analysis",
                    totalCvsReceived, candidatesScored);
        }

        // AI job profile extraction (only if AI mode)
        AiJobDescriptionProfile aiJobProfile = null;
        if (effectiveMode != ScoringMode.heuristic) {
            emitProgress(progressListener, "scoring", 0, candidatesScored, null,
                    "Preparing AI analysis...");
            try {
                aiJobProfile = jobDescriptionAiExtractor.orElseThrow().extract(jobDescription);
                log.info("AI job description extraction succeeded: roleTitle='{}'", aiJobProfile.roleTitle());
            } catch (Exception ex) {
                log.warn("AI job description extraction failed, batch will use heuristic fallback: {}", ex.getMessage());
                effectiveMode = ScoringMode.heuristic;
                emitProgress(progressListener, "scoring", 0, candidatesScored, null,
                        "AI job profile failed. Switching to heuristic scoring.");
            }
        }

        AnalysisOutcome analysisOutcome = analyseCandidates(
                jobDescriptionProfile,
                aiJobProfile,
                effectiveMode,
                outcomesForAnalysis,
                progressListener
        );
        List<CandidateEvaluation> evaluations = analysisOutcome.evaluations();

        if (analysisOutcome.anyAiFallback() && effectiveMode == ScoringMode.ai) {
            effectiveMode = ScoringMode.ai_with_fallbacks;
        }

        emitProgress(progressListener, "finalising", candidatesScored, candidatesScored, null,
                "Ranking and shortlisting candidates...");
        ScreeningResult screeningResult = new ScreeningResult(
                jobDescriptionProfile,
                shortlistService.shortlist(rankingService.rank(evaluations), effectiveShortlistCount, effectiveMinimumScore)
        );

        String aiJobProfileJson = aiJobProfile != null ? safeSerialize(aiJobProfile) : null;
        String promptVersions = effectiveMode != ScoringMode.heuristic
                ? com.recruiter.ai.AiPromptVersions.JOB_EXTRACTOR + "," + com.recruiter.ai.AiPromptVersions.CANDIDATE_EXTRACTOR + "," + com.recruiter.ai.AiPromptVersions.FIT_ASSESSOR
                : null;

        Long batchId = screeningBatchPersistenceService.save(
                jobDescription, effectiveShortlistCount, effectiveMode,
                totalCvsReceived, candidatesScored, effectiveMinimumScore,
                aiJobProfileJson, promptVersions, screeningResult);
        log.info("Screening request persisted: batchId={}, mode={}", batchId, effectiveMode);
        return new ScreeningRunResult(batchId, effectiveShortlistCount, effectiveMode,
                totalCvsReceived, candidatesScored, screeningResult);
    }

    private AnalysisOutcome analyseCandidates(JobDescriptionProfile jobDescriptionProfile,
                                              AiJobDescriptionProfile aiJobProfile,
                                              ScoringMode effectiveMode,
                                              List<DocumentExtractionOutcome> outcomesForAnalysis,
                                              ScreeningProgressListener progressListener) {
        if (effectiveMode != ScoringMode.heuristic && aiJobProfile != null) {
            return analyseCandidatesWithAi(jobDescriptionProfile, aiJobProfile, outcomesForAnalysis, progressListener);
        }
        return new AnalysisOutcome(
                analyseCandidatesSequentially(jobDescriptionProfile, outcomesForAnalysis, progressListener),
                false
        );
    }

    private AnalysisOutcome analyseCandidatesWithAi(JobDescriptionProfile jobDescriptionProfile,
                                                    AiJobDescriptionProfile aiJobProfile,
                                                    List<DocumentExtractionOutcome> outcomesForAnalysis,
                                                    ScreeningProgressListener progressListener) {
        AtomicBoolean anyAiFallback = new AtomicBoolean(false);
        AtomicInteger completed = new AtomicInteger(0);
        int total = outcomesForAnalysis.size();

        List<CompletableFuture<CandidateEvaluation>> futures = outcomesForAnalysis.stream()
                .map(outcome -> CompletableFuture.supplyAsync(() ->
                                evaluateCandidateWithAiFallback(jobDescriptionProfile, aiJobProfile, outcome, anyAiFallback),
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
                                                                    List<DocumentExtractionOutcome> outcomesForAnalysis,
                                                                    ScreeningProgressListener progressListener) {
        List<CandidateEvaluation> evaluations = new ArrayList<>(outcomesForAnalysis.size());
        int total = outcomesForAnalysis.size();
        int completed = 0;

        for (DocumentExtractionOutcome extractionOutcome : outcomesForAnalysis) {
            CandidateEvaluation evaluation = evaluateHeuristically(jobDescriptionProfile, extractionOutcome);
            evaluations.add(evaluation);
            completed++;
            emitProgress(progressListener, "scoring", completed, total,
                    evaluation.candidateProfile().candidateName(),
                    "Analysing candidate " + completed + " of " + total + "...");
        }
        return evaluations;
    }

    private List<DocumentExtractionOutcome> reduceToAnalysisCap(List<DocumentExtractionOutcome> allOutcomes,
                                                                  JobDescriptionProfile jobDescriptionProfile) {
        int analysisCap = properties.getAnalysisCap();

        List<DocumentExtractionOutcome> readable = allOutcomes.stream()
                .filter(DocumentExtractionOutcome::succeeded)
                .toList();
        List<DocumentExtractionOutcome> failed = allOutcomes.stream()
                .filter(outcome -> !outcome.succeeded())
                .toList();

        if (readable.size() <= analysisCap) {
            return allOutcomes;
        }

        // Score all readable CVs cheaply using heuristic matching, keep top N
        List<ScoredOutcome> scored = readable.stream()
                .map(outcome -> {
                    CandidateProfile profile = candidateProfileFactory.create(outcome.extractedDocument());
                    CandidateEvaluation eval = candidateScoringService.evaluate(jobDescriptionProfile, profile);
                    return new ScoredOutcome(outcome, eval.score());
                })
                .sorted(Comparator.comparingDouble(ScoredOutcome::score).reversed())
                .toList();

        List<DocumentExtractionOutcome> topReadable = scored.stream()
                .limit(analysisCap)
                .map(ScoredOutcome::outcome)
                .toList();

        // Include failed outcomes so they're still reported (they don't consume analysis budget)
        List<DocumentExtractionOutcome> result = new ArrayList<>(topReadable.size() + failed.size());
        result.addAll(topReadable);
        result.addAll(failed);
        return result;
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
                                                  DocumentExtractionOutcome extractionOutcome) {
        try {
            String cvText = extractionOutcome.extractedDocument().text();
            AiCandidateProfile aiCandidateProfile = candidateAiExtractor.orElseThrow().extract(cvText);
            CandidateProfile candidateProfile = candidateProfileFactory.create(extractionOutcome.extractedDocument());
            AiFitAssessment fitAssessment = fitAssessmentAiService.orElseThrow()
                    .assess(aiJobProfile, aiCandidateProfile);
            return aiMapper.map(candidateProfile, fitAssessment);
        } catch (Exception ex) {
            log.warn("AI evaluation failed for '{}': {}", extractionOutcome.originalFilename(), ex.getMessage());
            return null;
        }
    }

    private CandidateEvaluation evaluateCandidateWithAiFallback(JobDescriptionProfile jobDescriptionProfile,
                                                                AiJobDescriptionProfile aiJobProfile,
                                                                DocumentExtractionOutcome extractionOutcome,
                                                                AtomicBoolean anyAiFallback) {
        log.info("Candidate processing started: filename='{}', mode={}", extractionOutcome.originalFilename(), ScoringMode.ai);

        if (!extractionOutcome.succeeded()) {
            return buildExtractionFailureEvaluation(extractionOutcome);
        }

        CandidateEvaluation aiEval = tryAiEvaluation(aiJobProfile, extractionOutcome);
        if (aiEval != null) {
            log.info("AI candidate processing finished: filename='{}', score={}",
                    extractionOutcome.originalFilename(), aiEval.score());
            return aiEval;
        }

        anyAiFallback.set(true);
        log.warn("AI candidate processing failed, falling back to heuristic: filename='{}'",
                extractionOutcome.originalFilename());
        CandidateEvaluation fallbackEval = markHeuristicFallback(
                buildHeuristicEvaluation(jobDescriptionProfile, extractionOutcome)
        );
        log.info("Heuristic fallback candidate processing finished: filename='{}', score={}",
                extractionOutcome.originalFilename(), fallbackEval.score());
        return fallbackEval;
    }

    private CandidateEvaluation evaluateHeuristically(JobDescriptionProfile jobDescriptionProfile,
                                                      DocumentExtractionOutcome extractionOutcome) {
        log.info("Candidate processing started: filename='{}', mode={}",
                extractionOutcome.originalFilename(), ScoringMode.heuristic);
        if (!extractionOutcome.succeeded()) {
            return buildExtractionFailureEvaluation(extractionOutcome);
        }

        CandidateEvaluation heuristicEval = buildHeuristicEvaluation(jobDescriptionProfile, extractionOutcome);
        log.info("Heuristic candidate processing finished: filename='{}', score={}",
                extractionOutcome.originalFilename(), heuristicEval.score());
        return heuristicEval;
    }

    private CandidateEvaluation buildHeuristicEvaluation(JobDescriptionProfile jobDescriptionProfile,
                                                          DocumentExtractionOutcome extractionOutcome) {
        CandidateProfile candidateProfile = candidateProfileFactory.create(extractionOutcome.extractedDocument());
        return candidateScoringService.evaluate(jobDescriptionProfile, candidateProfile);
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

    private record ScoredOutcome(DocumentExtractionOutcome outcome, double score) {
    }

    private record AnalysisOutcome(List<CandidateEvaluation> evaluations, boolean anyAiFallback) {
    }
}

// Future orchestration hook: an agent framework such as Embabel could wrap
// job extraction, candidate extraction, fit assessment, and ranking
// as coordinated agent actions. The current sequential flow in screen()
// maps directly to such an orchestration pattern.
