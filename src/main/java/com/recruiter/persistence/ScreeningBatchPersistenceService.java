package com.recruiter.persistence;

import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.ScreeningResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ScreeningBatchPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ScreeningBatchPersistenceService.class);

    private final ScreeningBatchRepository screeningBatchRepository;

    @Transactional
    public Long save(String jobDescriptionText, int shortlistCount, ScreeningResult screeningResult) {
        ScreeningBatchEntity screeningBatch = new ScreeningBatchEntity();
        screeningBatch.setJobDescriptionText(jobDescriptionText);
        screeningBatch.setShortlistCount(shortlistCount);

        int rankPosition = 1;
        for (CandidateEvaluation evaluation : screeningResult.candidateEvaluations()) {
            screeningBatch.addCandidateEvaluation(toEntity(evaluation, rankPosition));
            rankPosition++;
        }

        ScreeningBatchEntity savedBatch = screeningBatchRepository.save(screeningBatch);
        log.info("Persisted screening batch: batchId={}, candidates={}, shortlisted={}",
                savedBatch.getId(),
                savedBatch.getCandidateEvaluations().size(),
                screeningResult.shortlistedCandidates().size());
        return savedBatch.getId();
    }

    private CandidateEvaluationEntity toEntity(CandidateEvaluation evaluation, int rankPosition) {
        CandidateEvaluationEntity entity = new CandidateEvaluationEntity();
        entity.setCandidateFilename(evaluation.candidateProfile().sourceFilename());
        entity.setScore(BigDecimal.valueOf(evaluation.score()));
        entity.setSummary(evaluation.summary());
        entity.setRankPosition(rankPosition);
        entity.setShortlisted(evaluation.shortlisted());
        return entity;
    }
}
