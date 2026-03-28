package com.recruiter.screening;

import com.recruiter.document.CvTextExtractionService;
import com.recruiter.document.DocumentExtractionOutcome;
import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.JobDescriptionProfile;
import com.recruiter.domain.ScreenedBatch;
import com.recruiter.domain.ScreeningResult;
import com.recruiter.persistence.ScreeningBatchPersistenceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CandidateScreeningFacade {

    private static final Logger log = LoggerFactory.getLogger(CandidateScreeningFacade.class);

    private final CvTextExtractionService cvTextExtractionService;
    private final CandidateScreeningOrchestrator candidateScreeningOrchestrator;
    private final CandidateEvaluationService candidateEvaluationService;
    private final RankingService rankingService;
    private final ShortlistService shortlistService;
    private final ScreeningBatchPersistenceService screeningBatchPersistenceService;

    public ScreenedBatch screen(String jobDescription, Integer shortlistCount, List<MultipartFile> cvFiles) {
        JobDescriptionProfile jobDescriptionProfile =
                candidateScreeningOrchestrator.extractJobDescriptionProfile(jobDescription);
        List<DocumentExtractionOutcome> extractionOutcomes = cvTextExtractionService.extractAll(cvFiles);
        int effectiveShortlistCount = shortlistService.resolveShortlistCount(shortlistCount);
        List<CandidateEvaluation> evaluations = candidateEvaluationService.evaluateAll(jobDescriptionProfile, extractionOutcomes);

        ScreeningResult screeningResult = new ScreeningResult(
                jobDescriptionProfile,
                shortlistService.shortlist(rankingService.rank(evaluations), effectiveShortlistCount)
        );

        Long batchId = screeningBatchPersistenceService.save(jobDescription, effectiveShortlistCount, screeningResult);
        log.info("Screening request persisted: batchId={}", batchId);
        return new ScreenedBatch(batchId, screeningResult);
    }
}
