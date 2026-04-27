package com.recruiter.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScreeningBatchRepository extends JpaRepository<ScreeningBatchEntity, Long> {

    @EntityGraph(attributePaths = "candidateEvaluations")
    List<ScreeningBatchEntity> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "candidateEvaluations")
    @Query("select screeningBatch from ScreeningBatchEntity screeningBatch where screeningBatch.id = :id")
    Optional<ScreeningBatchEntity> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = "eliminatedCandidates")
    @Query("select screeningBatch from ScreeningBatchEntity screeningBatch where screeningBatch.id = :id")
    Optional<ScreeningBatchEntity> findWithEliminatedCandidatesById(@Param("id") Long id);

    @Query("select coalesce(sum(screeningBatch.aiTotalTokens), 0), coalesce(sum(screeningBatch.aiEstimatedCostUsd), 0), count(screeningBatch) "
            + "from ScreeningBatchEntity screeningBatch where screeningBatch.aiTotalTokens is not null")
    Object[] findTotalAiUsage();

    @Modifying
    @Query("update ScreeningBatchEntity screeningBatch set screeningBatch.processingTimeMs = :processingTimeMs "
            + "where screeningBatch.id = :id")
    void updateProcessingTime(@Param("id") Long id, @Param("processingTimeMs") Long processingTimeMs);

    @Query("select b.processingTimeMs from ScreeningBatchEntity b "
            + "where b.processingTimeMs is not null order by b.processingTimeMs asc")
    List<Long> findAllProcessingTimesAsc();

    @Query("select coalesce(avg(b.aiEstimatedCostUsd), 0) from ScreeningBatchEntity b "
            + "where b.aiEstimatedCostUsd is not null and b.shortlistCount > 0")
    java.math.BigDecimal findAvgCostPerSuccessfulBatch();

    @Query("select b.screeningPackage, avg(b.aiEstimatedCostUsd), avg(b.processingTimeMs), count(b) "
            + "from ScreeningBatchEntity b "
            + "where b.screeningPackage is not null "
            + "group by b.screeningPackage order by b.screeningPackage")
    List<Object[]> findMetricsByPackage();

    @Query("select b.scoringMode, avg(b.aiEstimatedCostUsd), avg(b.processingTimeMs), count(b) "
            + "from ScreeningBatchEntity b "
            + "where b.scoringMode is not null "
            + "group by b.scoringMode order by b.scoringMode")
    List<Object[]> findMetricsByScoringMode();
}
