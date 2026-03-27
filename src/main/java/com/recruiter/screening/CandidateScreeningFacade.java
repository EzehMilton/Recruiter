package com.recruiter.screening;

import com.recruiter.document.CvTextExtractionService;
import com.recruiter.document.DocumentExtractionOutcome;
import com.recruiter.document.ExtractedDocument;
import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.CandidateProfile;
import com.recruiter.domain.JobDescriptionProfile;
import com.recruiter.domain.ScreeningResult;
import com.recruiter.persistence.ScreeningBatchPersistenceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

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

    public ScreeningResult screen(String jobDescription, Integer shortlistCount, List<MultipartFile> cvFiles) {
        JobDescriptionProfile jobDescriptionProfile = jobDescriptionProfileFactory.create(jobDescription);
        List<DocumentExtractionOutcome> extractionOutcomes = cvTextExtractionService.extractAll(cvFiles);
        int effectiveShortlistCount = shortlistService.resolveShortlistCount(shortlistCount);

        List<CandidateEvaluation> evaluations = new ArrayList<>(extractionOutcomes.size());
        for (DocumentExtractionOutcome extractionOutcome : extractionOutcomes) {
            log.info("Candidate processing started: filename='{}'", extractionOutcome.originalFilename());
            evaluations.add(buildEvaluation(jobDescriptionProfile, extractionOutcome));
        }

        ScreeningResult screeningResult = new ScreeningResult(
                jobDescriptionProfile,
                shortlistService.shortlist(rankingService.rank(evaluations), effectiveShortlistCount)
        );

        Long batchId = screeningBatchPersistenceService.save(jobDescription, effectiveShortlistCount, screeningResult);
        log.info("Screening request persisted: batchId={}", batchId);
        return screeningResult;
    }

    private CandidateEvaluation buildEvaluation(JobDescriptionProfile jobDescriptionProfile,
                                                DocumentExtractionOutcome extractionOutcome) {
        if (extractionOutcome.succeeded()) {
            CandidateProfile candidateProfile = candidateProfileFactory.create(extractionOutcome.extractedDocument());
            CandidateEvaluation evaluation = candidateScoringService.evaluate(jobDescriptionProfile, candidateProfile);
            log.info("Candidate processing finished: filename='{}', score={}",
                    extractionOutcome.originalFilename(), evaluation.score());
            return evaluation;
        }

        log.warn("Candidate processing failed: filename='{}', reason='{}'",
                extractionOutcome.originalFilename(), extractionOutcome.failureMessage());
        CandidateProfile failedCandidateProfile = candidateProfileFactory.create(
                new ExtractedDocument(extractionOutcome.originalFilename(), "")
        );
        return new CandidateEvaluation(
                failedCandidateProfile,
                0.0,
                "CV extraction failed for '" + extractionOutcome.originalFilename() + "'. " + extractionOutcome.failureMessage(),
                false
        );
    }
}
