package com.recruiter.persistence;

public record ScreeningBatchHistoryItem(
        Long batchId,
        String createdAtDisplay,
        int candidateCount,
        int shortlistCount
) {
}
