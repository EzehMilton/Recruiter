package com.recruiter.persistence;

import com.recruiter.document.ExtractedDocument;
import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.CandidateProfile;
import com.recruiter.domain.JobDescriptionProfile;
import com.recruiter.domain.ScreeningResult;
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
                screeningResult
        );
    }

    private CandidateEvaluation toCandidateEvaluation(CandidateEvaluationEntity entity) {
        CandidateProfile candidateProfile = candidateProfileFactory.create(
                new ExtractedDocument(entity.getCandidateFilename(), "")
        );
        return new CandidateEvaluation(
                candidateProfile,
                entity.getScore().doubleValue(),
                entity.getSummary(),
                entity.isShortlisted()
        );
    }

    private String formatTimestamp(java.time.Instant instant) {
        return TIMESTAMP_FORMATTER.format(instant);
    }
}
