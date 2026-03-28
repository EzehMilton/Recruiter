package com.recruiter.persistence;

public record ScreeningBatchHistoryItem(
        Long batchId,
        String createdAtDisplay,
        int candidateCount,
        int shortlistCount,
        String scoringMode,
        int totalCvsReceived,
        int candidatesScored
) {
}
