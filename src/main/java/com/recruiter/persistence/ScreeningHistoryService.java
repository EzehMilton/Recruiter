package com.recruiter.persistence;

import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.CandidateProfile;
import com.recruiter.domain.CandidateScoreBreakdown;
import com.recruiter.domain.JobDescriptionProfile;
import com.recruiter.domain.ScreeningResult;
import com.recruiter.document.ExtractedDocument;
import com.recruiter.screening.CandidateProfileFactory;
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
                        batch.getShortlistCount()))
                .toList();
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
        return new CandidateEvaluation(
                candidateProfile,
                entity.getScore().doubleValue(),
                new CandidateScoreBreakdown(
                        decimalOrZero(entity.getSkillScore()),
                        decimalOrZero(entity.getKeywordScore()),
                        decimalOrZero(entity.getExperienceScore())
                ),
                entity.getSummary(),
                entity.isShortlisted()
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
