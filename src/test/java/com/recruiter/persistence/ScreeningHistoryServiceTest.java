package com.recruiter.persistence;

import com.recruiter.domain.CandidateProfile;
import com.recruiter.service.CandidateProfileFactory;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ScreeningHistoryServiceTest {

    private final CandidateProfileFactory candidateProfileFactory = extractedDocument -> new CandidateProfile(
            "Fallback Name",
            extractedDocument.originalFilename(),
            extractedDocument.text(),
            List.of("Fallback Skill"),
            null
    );

    @Test
    void listHistoryIncludesAiUsageAndProcessingTimeDisplays() {
        StubScreeningBatchRepository stubRepository = new StubScreeningBatchRepository();
        ScreeningBatchEntity batch = batchEntity(42L, Instant.parse("2026-03-29T10:15:30Z"));
        batch.setShortlistCount(3);
        batch.setScoringMode("ai");
        batch.setTotalCvsReceived(25);
        batch.setCandidatesScored(20);
        batch.setAiTotalTokens(1_247);
        batch.setAiEstimatedCostUsd(new BigDecimal("0.0004"));
        batch.setProcessingTimeMs(8_800L);
        batch.addCandidateEvaluation(candidateEvaluationEntity("Ava Stone", "ava.pdf", 1));

        stubRepository.historyBatches = List.of(batch);
        ScreeningHistoryService screeningHistoryService =
                new ScreeningHistoryService(stubRepository.proxy(), candidateProfileFactory);

        ScreeningBatchHistoryItem item = screeningHistoryService.listHistory().getFirst();

        assertThat(item.batchId()).isEqualTo(42L);
        assertThat(item.aiUsageDisplay()).isEqualTo("1,247 tokens (~$0.0004)");
        assertThat(item.processingTimeDisplay()).isEqualTo("8.8s");
    }

    @Test
    void totalAiUsageSummarisesRepositoryAggregation() {
        StubScreeningBatchRepository stubRepository = new StubScreeningBatchRepository();
        stubRepository.totalAiUsageRow = new Object[]{48_392L, new BigDecimal("0.0231"), 12L};
        ScreeningHistoryService screeningHistoryService =
                new ScreeningHistoryService(stubRepository.proxy(), candidateProfileFactory);

        AiUsageSummary summary = screeningHistoryService.totalAiUsage();

        assertThat(summary.totalTokens()).isEqualTo(48_392L);
        assertThat(summary.batchCount()).isEqualTo(12L);
        assertThat(summary.displayText()).isEqualTo("Total AI usage: 48,392 tokens across 12 batches (~$0.02)");
    }

    @Test
    void findBatchAndEliminatedCandidatesMapsStoredMetricsAndSortsEliminatedRows() {
        StubScreeningBatchRepository stubRepository = new StubScreeningBatchRepository();
        ScreeningBatchEntity batch = batchEntity(99L, Instant.parse("2026-03-29T12:00:00Z"));
        batch.setShortlistCount(2);
        batch.setScoringMode("ai_with_fallbacks");
        batch.setTotalCvsReceived(30);
        batch.setCandidatesScored(20);
        batch.setAiPromptTokens(1_890);
        batch.setAiCompletionTokens(450);
        batch.setAiTotalTokens(2_340);
        batch.setAiEstimatedCostUsd(new BigDecimal("0.0009"));
        batch.setProcessingTimeMs(9_500L);
        batch.addCandidateEvaluation(candidateEvaluationEntity("Milton Ezeh", "milton.pdf", 1));
        batch.addEliminatedCandidate(eliminatedCandidateEntity("Lower Match", "low.pdf", "17.0", "Excel\nAdmin"));
        batch.addEliminatedCandidate(eliminatedCandidateEntity("Higher Match", "high.pdf", "24.0", "Actuarial\nReserving"));

        stubRepository.detailedBatches.put(99L, batch);
        stubRepository.eliminatedBatches.put(99L, batch);
        ScreeningHistoryService screeningHistoryService =
                new ScreeningHistoryService(stubRepository.proxy(), candidateProfileFactory);

        StoredScreeningBatchResult storedBatch = screeningHistoryService.findBatch(99L).orElseThrow();
        List<StoredEliminatedCandidate> eliminated = screeningHistoryService.findEliminatedCandidates(99L).orElseThrow();

        assertThat(storedBatch.hasAiUsage()).isTrue();
        assertThat(storedBatch.aiUsageDisplay()).isEqualTo("2,340 tokens (~$0.0009)");
        assertThat(storedBatch.processingTimeDisplay()).isEqualTo("9.5s");
        assertThat(storedBatch.screeningResult().candidateEvaluations()).hasSize(1);
        assertThat(storedBatch.screeningResult().candidateEvaluations().getFirst().candidateProfile().candidateName())
                .isEqualTo("Milton Ezeh");

        assertThat(eliminated).hasSize(2);
        assertThat(eliminated.getFirst().candidateName()).isEqualTo("Higher Match");
        assertThat(eliminated.getFirst().matchedSkills()).containsExactly("Actuarial", "Reserving");
        assertThat(eliminated.get(1).candidateName()).isEqualTo("Lower Match");
    }

    private ScreeningBatchEntity batchEntity(Long id, Instant createdAt) {
        ScreeningBatchEntity batch = new ScreeningBatchEntity();
        ReflectionTestUtils.setField(batch, "id", id);
        ReflectionTestUtils.setField(batch, "createdAt", createdAt);
        return batch;
    }

    private CandidateEvaluationEntity candidateEvaluationEntity(String candidateName, String filename, int rankPosition) {
        CandidateEvaluationEntity entity = new CandidateEvaluationEntity();
        entity.setCandidateName(candidateName);
        entity.setCandidateFilename(filename);
        entity.setExtractedSkills("Java\nSpring Boot");
        entity.setYearsOfExperience(6);
        entity.setScore(new BigDecimal("48.0"));
        entity.setSkillScore(new BigDecimal("20.0"));
        entity.setKeywordScore(new BigDecimal("18.0"));
        entity.setExperienceScore(new BigDecimal("10.0"));
        entity.setSummary("Strong technical fit.");
        entity.setScoringPath("ai");
        entity.setRankPosition(rankPosition);
        entity.setShortlisted(true);
        return entity;
    }

    private EliminatedCandidateEntity eliminatedCandidateEntity(String candidateName,
                                                                String filename,
                                                                String score,
                                                                String matchedSkills) {
        EliminatedCandidateEntity entity = new EliminatedCandidateEntity();
        entity.setCandidateName(candidateName);
        entity.setCandidateFilename(filename);
        entity.setPreFilterScore(new BigDecimal(score));
        entity.setMatchedSkills(matchedSkills);
        return entity;
    }

    private static final class StubScreeningBatchRepository {

        private List<ScreeningBatchEntity> historyBatches = List.of();
        private Object[] totalAiUsageRow = new Object[]{0L, BigDecimal.ZERO, 0L};
        private final Map<Long, ScreeningBatchEntity> detailedBatches = new HashMap<>();
        private final Map<Long, ScreeningBatchEntity> eliminatedBatches = new HashMap<>();

        private ScreeningBatchRepository proxy() {
            return (ScreeningBatchRepository) Proxy.newProxyInstance(
                    ScreeningBatchRepository.class.getClassLoader(),
                    new Class[]{ScreeningBatchRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findAllByOrderByCreatedAtDesc" -> historyBatches;
                        case "findTotalAiUsage" -> totalAiUsageRow;
                        case "findDetailedById" -> Optional.ofNullable(detailedBatches.get((Long) args[0]));
                        case "findWithEliminatedCandidatesById" -> Optional.ofNullable(eliminatedBatches.get((Long) args[0]));
                        case "toString" -> "StubScreeningBatchRepository";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException("Unexpected repository method: " + method.getName());
                    }
            );
        }
    }
}
