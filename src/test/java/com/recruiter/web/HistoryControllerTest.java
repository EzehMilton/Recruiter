package com.recruiter.web;

import com.recruiter.ai.AiResult;
import com.recruiter.ai.TokenUsage;
import com.recruiter.config.RecruitmentProperties;
import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.CandidateProfile;
import com.recruiter.domain.JobDescriptionProfile;
import com.recruiter.domain.ScreeningResult;
import com.recruiter.persistence.ScreeningHistoryService;
import com.recruiter.persistence.StoredCandidateDetail;
import com.recruiter.persistence.StoredEliminatedCandidate;
import com.recruiter.persistence.StoredScreeningBatchResult;
import com.recruiter.report.CandidateReportNarrative;
import com.recruiter.report.CandidateReportNarrativeService;
import com.recruiter.report.InterviewQuestion;
import com.recruiter.report.ReportNarrative;
import com.recruiter.report.ReportNarrativeService;
import com.recruiter.domain.CandidateScoreBreakdown;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryControllerTest {

    private static final RecruitmentProperties PROPERTIES = new RecruitmentProperties();

    @Test
    void historyDetailAddsStoredSectorDisplayToResultsModel() {
        HistoryController controller = new HistoryController(new ScreeningHistoryService(null, null) {
            @Override
            public Optional<StoredScreeningBatchResult> findBatch(Long batchId) {
                return Optional.of(storedBatch(batchId, "HEALTHCARE"));
            }
        }, request -> new AiResult<>(ReportNarrative.empty(), TokenUsage.ZERO),
                request -> new AiResult<>(CandidateReportNarrative.empty(), TokenUsage.ZERO),
                PROPERTIES);
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
        }, request -> new AiResult<>(ReportNarrative.empty(), TokenUsage.ZERO),
                request -> new AiResult<>(CandidateReportNarrative.empty(), TokenUsage.ZERO),
                PROPERTIES);
        ExtendedModelMap model = new ExtendedModelMap();

        String viewName = controller.historyDetail(8L, model);

        assertThat(viewName).isEqualTo("results");
        assertThat(model.get("sectorDisplay")).isEqualTo("Default (Generic)");
    }

    @Test
    void screeningReportAddsNarrativeAiUsageToModel() {
        ReportNarrativeService reportService = request -> new AiResult<>(
                new ReportNarrative("Executive", "Method", "Next"),
                new TokenUsage(1000, 200, 1200)
        );
        HistoryController controller = new HistoryController(historyServiceWithBatch(), reportService,
                request -> new AiResult<>(CandidateReportNarrative.empty(), TokenUsage.ZERO),
                PROPERTIES);
        ExtendedModelMap model = new ExtendedModelMap();

        String viewName = controller.screeningReport(7L, model);

        assertThat(viewName).isEqualTo("report");
        assertThat(model.containsAttribute("reportAiUsageMessage")).isFalse();
    }

    @Test
    void candidateReportAddsNarrativeAiUsageToModel() {
        CandidateReportNarrativeService candidateReportService = request -> new AiResult<>(
                new CandidateReportNarrative(
                        "Summary",
                        List.of("Strength"),
                        List.of("Gap"),
                        "Fit",
                        List.of(new InterviewQuestion("Question", "Guide", List.of("Follow-up")))
                ),
                new TokenUsage(400, 100, 500)
        );
        HistoryController controller = new HistoryController(historyServiceWithBatch(),
                request -> new AiResult<>(ReportNarrative.empty(), TokenUsage.ZERO),
                candidateReportService,
                PROPERTIES);
        ExtendedModelMap model = new ExtendedModelMap();

        String viewName = controller.candidateReport(7L, 1, model);

        assertThat(viewName).isEqualTo("candidate-report");
        assertThat(model.containsAttribute("reportAiUsageMessage")).isFalse();
    }

    private ScreeningHistoryService historyServiceWithBatch() {
        return new ScreeningHistoryService(null, null) {
            @Override
            public Optional<StoredScreeningBatchResult> findBatch(Long batchId) {
                return Optional.of(storedBatch(batchId, "HEALTHCARE"));
            }

            @Override
            public Optional<StoredCandidateDetail> findCandidate(Long batchId, int rankPosition) {
                return Optional.of(new StoredCandidateDetail(
                        batchId,
                        "2026-04-13 19:21:54",
                        3,
                        rankPosition,
                        candidateEvaluation()
                ));
            }

            @Override
            public Optional<List<StoredEliminatedCandidate>> findEliminatedCandidates(Long batchId) {
                return Optional.of(List.of());
            }
        };
    }

    private CandidateEvaluation candidateEvaluation() {
        return new CandidateEvaluation(
                new CandidateProfile(
                        "Jane Doe",
                        "jane-doe.pdf",
                        "Candidate CV text",
                        List.of("Java"),
                        6
                ),
                82.0,
                new CandidateScoreBreakdown(56.0, 16.0, 10.0),
                "ai",
                "Strong match",
                true,
                "HIGH",
                List.of("Strength"),
                List.of("Gap"),
                List.of("Probe")
        );
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
