package com.recruiter.persistence;

import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.CandidateProfile;
import com.recruiter.domain.CandidateScoreBreakdown;
import com.recruiter.domain.JobDescriptionProfile;
import com.recruiter.domain.ScreeningResult;
import com.recruiter.document.ExtractedDocument;
import com.recruiter.service.CandidateProfileFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ScreeningHistoryService {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ScreeningBatchRepository screeningBatchRepository;
    private final CandidateProfileFactory candidateProfileFactory;

    @Transactional(readOnly = true)
    public List<ScreeningBatchHistoryItem> listHistory() {
        return screeningBatchRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(batch -> new ScreeningBatchHistoryItem(
                        batch.getId(),
                        formatTimestamp(batch.getCreatedAt()),
                        batch.getCandidateEvaluations().size(),
                        batch.getShortlistCount(),
                        batch.getScoringMode() != null ? batch.getScoringMode() : "heuristic",
                        batch.getTotalCvsReceived(),
                        batch.getCandidatesScored(),
                        batch.getAiTotalTokens(),
                        batch.getAiEstimatedCostUsd(),
                        batch.getProcessingTimeMs()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AiUsageSummary totalAiUsage() {
        Object[] row = screeningBatchRepository.findTotalAiUsage();
        // Issue fixed
        if (row == null || row.length < 3) {
            return new AiUsageSummary(0L, java.math.BigDecimal.ZERO, 0L);
        }
        long totalTokens = row[0] instanceof Number number ? number.longValue() : 0L;
        java.math.BigDecimal totalCost = row[1] instanceof java.math.BigDecimal value
                ? value
                : java.math.BigDecimal.ZERO;
        long batchCount = row[2] instanceof Number number ? number.longValue() : 0L;
        return new AiUsageSummary(totalTokens, totalCost, batchCount);
    }

    @Transactional(readOnly = true)
    public Optional<StoredScreeningBatchResult> findBatch(Long batchId) {
        return screeningBatchRepository.findDetailedById(batchId)
                .map(this::toStoredResult);
    }

    @Transactional(readOnly = true)
    public Optional<StoredCandidateDetail> findCandidate(Long batchId, int rankPosition) {
        return screeningBatchRepository.findDetailedById(batchId)
                .flatMap(batch -> batch.getCandidateEvaluations().stream()
                        .filter(candidateEvaluation -> candidateEvaluation.getRankPosition() == rankPosition)
                        .findFirst()
                        .map(candidateEvaluation -> new StoredCandidateDetail(
                                batch.getId(),
                                formatTimestamp(batch.getCreatedAt()),
                                batch.getShortlistCount(),
                                rankPosition,
                                toCandidateEvaluation(candidateEvaluation)
                        )));
    }

    @Transactional(readOnly = true)
    public Optional<List<StoredEliminatedCandidate>> findEliminatedCandidates(Long batchId) {
        return screeningBatchRepository.findWithEliminatedCandidatesById(batchId)
                .map(batch -> batch.getEliminatedCandidates().stream()
                        .sorted(Comparator.comparing(EliminatedCandidateEntity::getPreFilterScore).reversed())
                        .map(this::toStoredEliminatedCandidate)
                        .toList());
    }

    private StoredScreeningBatchResult toStoredResult(ScreeningBatchEntity batch) {
        List<CandidateEvaluation> evaluations = batch.getCandidateEvaluations().stream()
                .sorted(Comparator.comparingInt(CandidateEvaluationEntity::getRankPosition))
                .map(this::toCandidateEvaluation)
                .toList();

        ScreeningResult screeningResult = new ScreeningResult(
                new JobDescriptionProfile(batch.getJobDescriptionText(), List.of(), List.of(), null),
                evaluations
        );

        return new StoredScreeningBatchResult(
                batch.getId(),
                formatTimestamp(batch.getCreatedAt()),
                batch.getShortlistCount(),
                batch.getScoringMode(),
                batch.getTotalCvsReceived(),
                batch.getCandidatesScored(),
                batch.getAiPromptTokens(),
                batch.getAiCompletionTokens(),
                batch.getAiTotalTokens(),
                batch.getAiEstimatedCostUsd(),
                batch.getProcessingTimeMs(),
                screeningResult
        );
    }

    private CandidateEvaluation toCandidateEvaluation(CandidateEvaluationEntity entity) {
        CandidateProfile fallbackCandidateProfile = candidateProfileFactory.create(
                new ExtractedDocument(entity.getCandidateFilename(), "")
        );
        List<String> extractedSkills = splitSkills(entity.getExtractedSkills());
        CandidateProfile candidateProfile = new CandidateProfile(
                valueOrDefault(entity.getCandidateName(), fallbackCandidateProfile.candidateName()),
                entity.getCandidateFilename(),
                "",
                !extractedSkills.isEmpty() ? extractedSkills : fallbackCandidateProfile.extractedSkills(),
                entity.getYearsOfExperience() != null
                        ? entity.getYearsOfExperience()
                        : fallbackCandidateProfile.yearsOfExperience()
        );
        String scoringPath = entity.getScoringPath() != null ? entity.getScoringPath() : "heuristic";
        return new CandidateEvaluation(
                candidateProfile,
                entity.getScore().doubleValue(),
                new CandidateScoreBreakdown(
                        decimalOrZero(entity.getSkillScore()),
                        decimalOrZero(entity.getKeywordScore()),
                        decimalOrZero(entity.getExperienceScore())
                ),
                scoringPath,
                entity.getSummary(),
                entity.isShortlisted(),
                "", List.of(), List.of(), List.of()
        );
    }

    private StoredEliminatedCandidate toStoredEliminatedCandidate(EliminatedCandidateEntity entity) {
        return new StoredEliminatedCandidate(
                entity.getCandidateName(),
                entity.getCandidateFilename(),
                entity.getPreFilterScore().doubleValue(),
                splitSkills(entity.getMatchedSkills())
        );
    }

    private List<String> splitSkills(String extractedSkills) {
        if (extractedSkills == null || extractedSkills.isBlank()) {
            return List.of();
        }
        return extractedSkills.lines()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private String valueOrDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private double decimalOrZero(java.math.BigDecimal value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    private String formatTimestamp(java.time.Instant instant) {
        return TIMESTAMP_FORMATTER.format(instant);
    }
}
