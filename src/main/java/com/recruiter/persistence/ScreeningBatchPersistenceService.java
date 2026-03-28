package com.recruiter.persistence;

import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.ScoringMode;
import com.recruiter.domain.ScreeningResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.StringJoiner;

@Service
@RequiredArgsConstructor
public class ScreeningBatchPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ScreeningBatchPersistenceService.class);

    private final ScreeningBatchRepository screeningBatchRepository;

    @Transactional
    public Long save(String jobDescriptionText, int shortlistCount, ScoringMode scoringMode,
                      int totalCvsReceived, int candidatesScored, ScreeningResult screeningResult) {
        ScreeningBatchEntity screeningBatch = new ScreeningBatchEntity();
        screeningBatch.setJobDescriptionText(jobDescriptionText);
        screeningBatch.setShortlistCount(shortlistCount);
        screeningBatch.setScoringMode(scoringMode.name());
        screeningBatch.setTotalCvsReceived(totalCvsReceived);
        screeningBatch.setCandidatesScored(candidatesScored);

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
        entity.setCandidateName(evaluation.candidateProfile().candidateName());
        entity.setCandidateFilename(evaluation.candidateProfile().sourceFilename());
        entity.setExtractedSkills(joinSkills(evaluation.candidateProfile().extractedSkills()));
        entity.setYearsOfExperience(evaluation.candidateProfile().yearsOfExperience());
        entity.setScore(BigDecimal.valueOf(evaluation.score()));
        entity.setSkillScore(BigDecimal.valueOf(evaluation.scoreBreakdown().skillScore()));
        entity.setKeywordScore(BigDecimal.valueOf(evaluation.scoreBreakdown().keywordScore()));
        entity.setExperienceScore(BigDecimal.valueOf(evaluation.scoreBreakdown().experienceScore()));
        entity.setSummary(evaluation.summary());
        entity.setRankPosition(rankPosition);
        entity.setShortlisted(evaluation.shortlisted());
        return entity;
    }

    private String joinSkills(java.util.List<String> extractedSkills) {
        StringJoiner joiner = new StringJoiner("\n");
        for (String extractedSkill : extractedSkills) {
            joiner.add(extractedSkill);
        }
        return joiner.toString();
    }
}
