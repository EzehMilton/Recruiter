package com.recruiter.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;  // static instance, not injected
import com.recruiter.ai.TokenUsage;
import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.ScoringMode;
import com.recruiter.domain.ScreeningResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

@Service
@RequiredArgsConstructor
public class ScreeningBatchPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ScreeningBatchPersistenceService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ScreeningBatchRepository screeningBatchRepository;

    @Transactional
    public Long save(String jobDescriptionText, int shortlistCount, ScoringMode scoringMode,
                     String sector,
                     int totalCvsReceived, int candidatesScored,
                     double shortlistThreshold,
                     TokenUsage aiTokenUsage,
                     Double aiEstimatedCostUsd,
                     Long processingTimeMs,
                     String aiJobProfileJson, String promptVersions,
                     ScreeningResult screeningResult,
                     java.util.List<EliminatedCandidateSnapshot> eliminatedCandidates) {
        return save(jobDescriptionText, shortlistCount, scoringMode, "QUICK_SCREEN", sector,
                totalCvsReceived, candidatesScored, shortlistThreshold, aiTokenUsage,
                aiEstimatedCostUsd, processingTimeMs, aiJobProfileJson, promptVersions,
                screeningResult, eliminatedCandidates);
    }

    @Transactional
    public Long save(String jobDescriptionText, int shortlistCount, ScoringMode scoringMode,
                      String screeningPackage,
                      String sector,
                      int totalCvsReceived, int candidatesScored,
                      double shortlistThreshold,
                      TokenUsage aiTokenUsage,
                      Double aiEstimatedCostUsd,
                      Long processingTimeMs,
                      String aiJobProfileJson, String promptVersions,
                      ScreeningResult screeningResult,
                      java.util.List<EliminatedCandidateSnapshot> eliminatedCandidates) {
        ScreeningBatchEntity screeningBatch = new ScreeningBatchEntity();
        screeningBatch.setJobDescriptionText(jobDescriptionText);
        screeningBatch.setShortlistCount(shortlistCount);
        screeningBatch.setScoringMode(scoringMode.name());
        screeningBatch.setScreeningPackage(screeningPackage);
        screeningBatch.setSector(sector);
        screeningBatch.setTotalCvsReceived(totalCvsReceived);
        screeningBatch.setCandidatesScored(candidatesScored);
        screeningBatch.setShortlistThreshold(BigDecimal.valueOf(shortlistThreshold));
        if (aiTokenUsage != null && aiTokenUsage.totalTokens() > 0) {
            screeningBatch.setAiPromptTokens(aiTokenUsage.promptTokens());
            screeningBatch.setAiCompletionTokens(aiTokenUsage.completionTokens());
            screeningBatch.setAiTotalTokens(aiTokenUsage.totalTokens());
            screeningBatch.setAiEstimatedCostUsd(aiEstimatedCostUsd != null ? BigDecimal.valueOf(aiEstimatedCostUsd) : null);
        }
        screeningBatch.setProcessingTimeMs(processingTimeMs);
        screeningBatch.setAiJobDescriptionProfileJson(aiJobProfileJson);
        screeningBatch.setPromptVersions(promptVersions);

        int rankPosition = 1;
        for (CandidateEvaluation evaluation : screeningResult.candidateEvaluations()) {
            screeningBatch.addCandidateEvaluation(toEntity(evaluation, rankPosition));
            rankPosition++;
        }

        java.util.List<EliminatedCandidateSnapshot> eliminatedSnapshots =
                eliminatedCandidates != null ? eliminatedCandidates : java.util.List.of();
        for (EliminatedCandidateSnapshot eliminatedCandidate : eliminatedSnapshots) {
            screeningBatch.addEliminatedCandidate(toEntity(eliminatedCandidate));
        }

        ScreeningBatchEntity savedBatch = screeningBatchRepository.save(screeningBatch);
        log.info("Persisted screening batch: batchId={}, candidates={}, shortlisted={}",
                savedBatch.getId(),
                savedBatch.getCandidateEvaluations().size(),
                screeningResult.shortlistedCandidates().size());
        return savedBatch.getId();
    }

    @Transactional
    public void updateProcessingTime(Long batchId, long processingTimeMs) {
        screeningBatchRepository.updateProcessingTime(batchId, processingTimeMs);
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
        entity.setScoringPath(evaluation.scoringPath());
        entity.setSummary(evaluation.summary());
        entity.setRankPosition(rankPosition);
        entity.setShortlisted(evaluation.shortlisted());
        if (!evaluation.aiTopStrengths().isEmpty()
                || !evaluation.aiTopGaps().isEmpty()
                || !evaluation.aiInterviewProbeAreas().isEmpty()
                || !evaluation.aiConfidence().isBlank()) {
            try {
                Map<String, Object> aiJson = new LinkedHashMap<>();
                aiJson.put("topStrengths", evaluation.aiTopStrengths());
                aiJson.put("topGaps", evaluation.aiTopGaps());
                aiJson.put("interviewProbeAreas", evaluation.aiInterviewProbeAreas());
                aiJson.put("confidence", evaluation.aiConfidence());
                entity.setAiFitAssessmentJson(OBJECT_MAPPER.writeValueAsString(aiJson));
            } catch (Exception e) {
                log.warn("Could not serialise AI fit data for '{}': {}",
                        evaluation.candidateProfile().sourceFilename(), e.getMessage());
            }
        }
        return entity;
    }

    private EliminatedCandidateEntity toEntity(EliminatedCandidateSnapshot eliminatedCandidate) {
        EliminatedCandidateEntity entity = new EliminatedCandidateEntity();
        entity.setCandidateName(eliminatedCandidate.candidateName());
        entity.setCandidateFilename(eliminatedCandidate.candidateFilename());
        entity.setPreFilterScore(BigDecimal.valueOf(eliminatedCandidate.preFilterScore()));
        entity.setMatchedSkills(joinSkills(eliminatedCandidate.matchedSkills()));
        entity.setScoreLabel(eliminatedCandidate.scoreLabel());
        entity.setEliminationReason(eliminatedCandidate.eliminationReason());
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
