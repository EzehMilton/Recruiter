package com.recruiter.screening;

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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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

    public ScreeningRunResult screen(String jobDescription, Integer shortlistCount,
                                      Double minimumShortlistScore, String requestedScoringMode,
                                      List<MultipartFile> cvFiles) {
        int effectiveShortlistCount = shortlistService.resolveShortlistCount(shortlistCount);
        double effectiveMinimumScore = shortlistService.resolveMinimumScore(minimumShortlistScore);
        ScoringMode effectiveMode = resolveEffectiveScoringMode(requestedScoringMode);

        JobDescriptionProfile jobDescriptionProfile = jobDescriptionProfileFactory.create(jobDescription);
        List<DocumentExtractionOutcome> allOutcomes = cvTextExtractionService.extractAll(cvFiles);
        int totalCvsReceived = allOutcomes.size();

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
            try {
                aiJobProfile = jobDescriptionAiExtractor.orElseThrow().extract(jobDescription);
                log.info("AI job description extraction succeeded: roleTitle='{}'", aiJobProfile.roleTitle());
            } catch (Exception ex) {
                log.warn("AI job description extraction failed, batch will use heuristic fallback: {}", ex.getMessage());
                effectiveMode = ScoringMode.heuristic;
            }
        }

        // Full analysis of reduced set
        boolean anyAiFallback = false;
        List<CandidateEvaluation> evaluations = new ArrayList<>(outcomesForAnalysis.size());
        for (DocumentExtractionOutcome extractionOutcome : outcomesForAnalysis) {
            log.info("Candidate processing started: filename='{}', mode={}", extractionOutcome.originalFilename(), effectiveMode);

            if (!extractionOutcome.succeeded()) {
                log.warn("Candidate processing failed: filename='{}', reason='{}'",
                        extractionOutcome.originalFilename(), extractionOutcome.failureMessage());
                CandidateProfile failedProfile = candidateProfileFactory.create(
                        new ExtractedDocument(extractionOutcome.originalFilename(), ""));
                evaluations.add(new CandidateEvaluation(failedProfile, 0.0,
                        "CV extraction failed for '" + extractionOutcome.originalFilename() + "'. " + extractionOutcome.failureMessage(),
                        false));
                continue;
            }

            if (effectiveMode != ScoringMode.heuristic && aiJobProfile != null) {
                CandidateEvaluation aiEval = tryAiEvaluation(aiJobProfile, extractionOutcome);
                if (aiEval != null) {
                    log.info("AI candidate processing finished: filename='{}', score={}",
                            extractionOutcome.originalFilename(), aiEval.score());
                    evaluations.add(aiEval);
                    continue;
                }
                anyAiFallback = true;
                log.warn("AI candidate processing failed, falling back to heuristic: filename='{}'",
                        extractionOutcome.originalFilename());
            }

            CandidateEvaluation heuristicEval = buildHeuristicEvaluation(jobDescriptionProfile, extractionOutcome);
            log.info("Heuristic candidate processing finished: filename='{}', score={}",
                    extractionOutcome.originalFilename(), heuristicEval.score());
            evaluations.add(heuristicEval);
        }

        if (anyAiFallback && effectiveMode == ScoringMode.ai) {
            effectiveMode = ScoringMode.ai_with_fallbacks;
        }

        ScreeningResult screeningResult = new ScreeningResult(
                jobDescriptionProfile,
                shortlistService.shortlist(rankingService.rank(evaluations), effectiveShortlistCount, effectiveMinimumScore)
        );

        Long batchId = screeningBatchPersistenceService.save(
                jobDescription, effectiveShortlistCount, effectiveMode,
                totalCvsReceived, candidatesScored, screeningResult);
        log.info("Screening request persisted: batchId={}, mode={}", batchId, effectiveMode);
        return new ScreeningRunResult(batchId, effectiveShortlistCount, effectiveMode,
                totalCvsReceived, candidatesScored, screeningResult);
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

    private CandidateEvaluation buildHeuristicEvaluation(JobDescriptionProfile jobDescriptionProfile,
                                                          DocumentExtractionOutcome extractionOutcome) {
        CandidateProfile candidateProfile = candidateProfileFactory.create(extractionOutcome.extractedDocument());
        return candidateScoringService.evaluate(jobDescriptionProfile, candidateProfile);
    }

    private record ScoredOutcome(DocumentExtractionOutcome outcome, double score) {
    }
}
