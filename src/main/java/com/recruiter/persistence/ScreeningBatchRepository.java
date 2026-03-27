package com.recruiter.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
