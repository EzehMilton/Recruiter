package com.recruiter.web;

import com.recruiter.domain.JobDescriptionProfile;
import com.recruiter.domain.ScreeningResult;
import com.recruiter.persistence.ScreeningHistoryService;
import com.recruiter.persistence.StoredScreeningBatchResult;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryControllerTest {

    @Test
    void historyDetailAddsStoredSectorDisplayToResultsModel() {
        HistoryController controller = new HistoryController(new ScreeningHistoryService(null, null) {
            @Override
            public Optional<StoredScreeningBatchResult> findBatch(Long batchId) {
                return Optional.of(storedBatch(batchId, "HEALTHCARE"));
            }
        });
        ExtendedModelMap model = new ExtendedModelMap();

        String viewName = controller.historyDetail(7L, model);

        assertThat(viewName).isEqualTo("results");
        assertThat(model.get("sectorDisplay")).isEqualTo("Healthcare");
    }

    @Test
    void historyDetailFallsBackToGenericSectorDisplayWhenStoredSectorIsMissing() {
        HistoryController controller = new HistoryController(new ScreeningHistoryService(null, null) {
            @Override
            public Optional<StoredScreeningBatchResult> findBatch(Long batchId) {
                return Optional.of(storedBatch(batchId, null));
            }
        });
        ExtendedModelMap model = new ExtendedModelMap();

        String viewName = controller.historyDetail(8L, model);

        assertThat(viewName).isEqualTo("results");
        assertThat(model.get("sectorDisplay")).isEqualTo("Default (Generic)");
    }

    private StoredScreeningBatchResult storedBatch(Long batchId, String sector) {
        return new StoredScreeningBatchResult(
                batchId,
                "2026-04-13 19:21:54",
                3,
                "ai_with_fallbacks",
                sector,
                31,
                28,
                10,
                20,
                30,
                java.math.BigDecimal.valueOf(0.03),
                5000L,
                new ScreeningResult(
                        new JobDescriptionProfile("Java developer", List.of(), List.of(), null),
                        List.of()
                )
        );
    }
}
