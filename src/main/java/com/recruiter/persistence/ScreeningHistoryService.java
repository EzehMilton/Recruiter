package com.recruiter.persistence;

import com.recruiter.document.ExtractedDocument;
import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.CandidateProfile;
import com.recruiter.domain.JobDescriptionProfile;
import com.recruiter.domain.ScreeningResult;
import com.recruiter.screening.CandidateProfileExtractor;
import com.recruiter.screening.CandidateScoreDetails;
import com.recruiter.screening.CategoryScore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ScreeningHistoryService {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ScreeningBatchRepository screeningBatchRepository;
    private final CandidateProfileExtractor candidateProfileExtractor;

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
    public Optional<StoredCandidateDetail> findCandidate(Long batchId, int rank) {
        return screeningBatchRepository.findDetailedById(batchId)
                .flatMap(batch -> batch.getCandidateEvaluations().stream()
                        .filter(entity -> entity.getRankPosition() == rank)
                        .findFirst()
                        .map(entity -> new StoredCandidateDetail(
                                batch.getId(),
                                formatTimestamp(batch.getCreatedAt()),
                                toCandidateEvaluation(entity),
                                rank,
                                batch.getCandidateEvaluations().size()
                        )));
    }

    private StoredScreeningBatchResult toStoredResult(ScreeningBatchEntity batch) {
        List<CandidateEvaluation> evaluations = batch.getCandidateEvaluations().stream()
                .sorted(Comparator.comparingInt(CandidateEvaluationEntity::getRankPosition))
                .map(this::toCandidateEvaluation)
                .toList();

        ScreeningResult screeningResult = new ScreeningResult(
                new JobDescriptionProfile(batch.getJobDescriptionText(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), null),
                evaluations
        );

        return new StoredScreeningBatchResult(
                batch.getId(),
                formatTimestamp(batch.getCreatedAt()),
                batch.getShortlistCount(),
                screeningResult
        );
    }

    private CandidateEvaluation toCandidateEvaluation(CandidateEvaluationEntity entity) {
        CandidateProfile candidateProfile = candidateProfileExtractor.extract(
                new ExtractedDocument(entity.getCandidateFilename(), "")
        );

        CandidateScoreDetails scoreDetails = null;
        if (entity.getRequiredSkillsScore() != null) {
            double reqScore = safeDouble(entity.getRequiredSkillsScore());
            double prefScore = safeDouble(entity.getPreferredSkillsScore());
            double expScore = safeDouble(entity.getExperienceScore());
            double domScore = safeDouble(entity.getDomainRelevanceScore());
            double qualScore = safeDouble(entity.getQualificationsScore());
            double softScore = safeDouble(entity.getSoftSkillsScore());
            List<CategoryScore> breakdown = List.of(
                    new CategoryScore("Required Skills", reqScore, 0, 0.0),
                    new CategoryScore("Preferred Skills", prefScore, 0, 0.0),
                    new CategoryScore("Experience", expScore, 0, 0.0),
                    new CategoryScore("Domain Relevance", domScore, 0, 0.0),
                    new CategoryScore("Qualifications", qualScore, 0, 0.0),
                    new CategoryScore("Soft Skills", softScore, 0, 0.0)
            );
            scoreDetails = new CandidateScoreDetails(
                    entity.getScore().doubleValue(),
                    reqScore, prefScore, expScore, domScore, qualScore, softScore,
                    breakdown,
                    List.of(), List.of(), List.of(), List.of(), List.of(), Set.of()
            );
        }

        return new CandidateEvaluation(
                candidateProfile,
                entity.getScore().doubleValue(),
                scoreDetails,
                entity.getSummary(),
                entity.isShortlisted()
        );
    }

    private double safeDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    private String formatTimestamp(java.time.Instant instant) {
        return TIMESTAMP_FORMATTER.format(instant);
    }
}
