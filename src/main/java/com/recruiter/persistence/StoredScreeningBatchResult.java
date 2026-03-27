package com.recruiter.persistence;

import com.recruiter.domain.ScreeningResult;

public record StoredScreeningBatchResult(
        Long batchId,
        String createdAtDisplay,
        int shortlistCount,
        ScreeningResult screeningResult
) {
}
