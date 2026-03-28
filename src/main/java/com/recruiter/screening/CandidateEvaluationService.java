package com.recruiter.screening;

import com.recruiter.ai.AiClientException;
import com.recruiter.config.RecruitmentProperties;
import com.recruiter.document.DocumentExtractionOutcome;
import com.recruiter.document.ExtractedDocument;
import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.CandidateProfile;
import com.recruiter.domain.JobDescriptionProfile;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

@Service
@RequiredArgsConstructor
public class CandidateEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(CandidateEvaluationService.class);

    private final CandidateScreeningOrchestrator candidateScreeningOrchestrator;
    private final CandidateProfileExtractor candidateProfileExtractor;
    private final CandidateEvaluationFactory candidateEvaluationFactory;
    private final RecruitmentProperties properties;

    public List<CandidateEvaluation> evaluateAll(JobDescriptionProfile jobDescriptionProfile,
                                                 List<DocumentExtractionOutcome> extractionOutcomes) {
        if (extractionOutcomes == null || extractionOutcomes.isEmpty()) {
            return List.of();
        }

        int concurrencyLimit = Math.max(1, Math.min(
                properties.getMaxParallelCandidateEvaluations(),
                extractionOutcomes.size()));
        Semaphore permits = new Semaphore(concurrencyLimit);
        Map<String, CandidateEvaluation> evaluationCache = new ConcurrentHashMap<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<IndexedCandidateEvaluation>> futures = java.util.stream.IntStream.range(0, extractionOutcomes.size())
                    .mapToObj(index -> executor.submit(() ->
                            evaluateWithPermit(index, jobDescriptionProfile, extractionOutcomes.get(index),
                                    permits, evaluationCache)))
                    .toList();

            return futures.stream()
                    .map(this::await)
                    .sorted(Comparator.comparingInt(IndexedCandidateEvaluation::index))
                    .map(IndexedCandidateEvaluation::evaluation)
                    .toList();
        }
    }

    private IndexedCandidateEvaluation evaluateWithPermit(int index,
                                                          JobDescriptionProfile jobDescriptionProfile,
                                                          DocumentExtractionOutcome extractionOutcome,
                                                          Semaphore permits,
                                                          Map<String, CandidateEvaluation> evaluationCache) {
        boolean acquired = false;
        try {
            permits.acquire();
            acquired = true;
            return new IndexedCandidateEvaluation(index,
                    buildEvaluationCached(jobDescriptionProfile, extractionOutcome, evaluationCache));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Candidate processing interrupted before completion: filename='{}'",
                    extractionOutcome.originalFilename());
            return new IndexedCandidateEvaluation(
                    index,
                    failedCandidateEvaluation(
                            extractionOutcome.originalFilename(),
                            "Candidate processing was interrupted before completion."
                    )
            );
        } finally {
            if (acquired) {
                permits.release();
            }
        }
    }

    private CandidateEvaluation buildEvaluationCached(JobDescriptionProfile jobDescriptionProfile,
                                                       DocumentExtractionOutcome extractionOutcome,
                                                       Map<String, CandidateEvaluation> evaluationCache) {
        if (!extractionOutcome.succeeded()) {
            return buildEvaluation(jobDescriptionProfile, extractionOutcome);
        }

        String textDigest = sha256(extractionOutcome.extractedDocument().text());
        if (textDigest == null) {
            return buildEvaluation(jobDescriptionProfile, extractionOutcome);
        }

        CandidateEvaluation cached = evaluationCache.get(textDigest);
        if (cached != null) {
            log.info("Evaluation cache hit: filename='{}' has identical text to a previously evaluated candidate",
                    extractionOutcome.originalFilename());
            return cached;
        }

        CandidateEvaluation evaluation = buildEvaluation(jobDescriptionProfile, extractionOutcome);
        evaluationCache.put(textDigest, evaluation);
        return evaluation;
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            return null;
        }
    }

    private IndexedCandidateEvaluation await(Future<IndexedCandidateEvaluation> future) {
        try {
            return future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Candidate evaluation was interrupted", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Candidate evaluation failed unexpectedly", ex.getCause());
        }
    }

    private CandidateEvaluation buildEvaluation(JobDescriptionProfile jobDescriptionProfile,
                                                DocumentExtractionOutcome extractionOutcome) {
        log.info("Candidate processing started: filename='{}'", extractionOutcome.originalFilename());

        if (extractionOutcome.succeeded()) {
            try {
                CandidateEvaluation evaluation = candidateScreeningOrchestrator.evaluateCandidate(
                        jobDescriptionProfile,
                        extractionOutcome.extractedDocument()
                );
                log.info("Candidate processing finished: filename='{}', score={}",
                        extractionOutcome.originalFilename(), evaluation.score());
                return evaluation;
            } catch (AiClientException ex) {
                log.warn("Candidate AI processing failed: filename='{}', reason='{}'",
                        extractionOutcome.originalFilename(), ex.getMessage());
                return failedCandidateEvaluation(
                        extractionOutcome.originalFilename(),
                        "AI candidate processing failed. " + ex.getMessage()
                );
            } catch (RuntimeException ex) {
                log.warn("Candidate processing failed unexpectedly: filename='{}', reason='{}'",
                        extractionOutcome.originalFilename(), ex.getMessage());
                return failedCandidateEvaluation(
                        extractionOutcome.originalFilename(),
                        "Candidate processing failed unexpectedly. " + ex.getMessage()
                );
            }
        }

        log.warn("Candidate processing failed: filename='{}', reason='{}'",
                extractionOutcome.originalFilename(), extractionOutcome.failureMessage());
        return failedCandidateEvaluation(
                extractionOutcome.originalFilename(),
                "CV extraction failed for '" + extractionOutcome.originalFilename() + "'. " + extractionOutcome.failureMessage()
        );
    }

    private CandidateEvaluation failedCandidateEvaluation(String filename, String summary) {
        return candidateEvaluationFactory.createFailed(failedCandidateProfile(filename), summary);
    }

    private CandidateProfile failedCandidateProfile(String filename) {
        try {
            return candidateProfileExtractor.extract(new ExtractedDocument(filename, ""));
        } catch (RuntimeException ex) {
            log.warn("Failed to build fallback candidate profile: filename='{}', reason='{}'",
                    filename, ex.getMessage());
            return new CandidateProfile(filename, filename, "", List.of(), List.of(), List.of(), null);
        }
    }

    private record IndexedCandidateEvaluation(int index, CandidateEvaluation evaluation) {
    }
}
