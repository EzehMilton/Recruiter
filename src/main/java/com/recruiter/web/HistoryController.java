package com.recruiter.web;

import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.persistence.ScreeningHistoryService;
import com.recruiter.persistence.StoredCandidateDetail;
import com.recruiter.persistence.StoredEliminatedCandidate;
import com.recruiter.persistence.StoredScreeningBatchResult;
import com.recruiter.report.CandidateReportNarrative;
import com.recruiter.report.CandidateReportNarrativeService;
import com.recruiter.report.ReportNarrative;
import com.recruiter.report.ReportNarrativeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class HistoryController {

    private final ScreeningHistoryService screeningHistoryService;
    private final ReportNarrativeService reportNarrativeService;
    private final CandidateReportNarrativeService candidateReportNarrativeService;

    @GetMapping("/loading")
    public String loading(@org.springframework.web.bind.annotation.RequestParam String to, Model model) {
        model.addAttribute("targetUrl", to);
        return "loading";
    }

    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("batches", screeningHistoryService.listHistory());
        model.addAttribute("aiUsageSummary", screeningHistoryService.totalAiUsage());
        return "history";
    }

    @GetMapping("/history/{batchId}")
    public String historyDetail(@PathVariable Long batchId, Model model) {
        StoredScreeningBatchResult storedBatch = screeningHistoryService.findBatch(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Screening batch not found"));

        model.addAttribute("screeningResult", storedBatch.screeningResult());
        model.addAttribute("batchId", storedBatch.batchId());
        model.addAttribute("batchCreatedAtDisplay", storedBatch.createdAtDisplay());
        model.addAttribute("shortlistCount", storedBatch.shortlistCount());
        model.addAttribute("scoringMode", storedBatch.scoringMode());
        model.addAttribute("sectorDisplay", storedBatch.sectorDisplay());
        model.addAttribute("totalCvsReceived", storedBatch.totalCvsReceived());
        model.addAttribute("candidatesScored", storedBatch.candidatesScored());
        model.addAttribute("wasReduced", storedBatch.totalCvsReceived() > storedBatch.candidatesScored());
        model.addAttribute("aiUsageDisplay", storedBatch.aiUsageDisplay());
        return "results";
    }

    @GetMapping("/history/{batchId}/candidates/{rankPosition}")
    public String candidateDetail(@PathVariable Long batchId,
                                  @PathVariable int rankPosition,
                                  Model model) {
        StoredCandidateDetail storedCandidate = screeningHistoryService.findCandidate(batchId, rankPosition)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found"));

        model.addAttribute("candidateEvaluation", storedCandidate.candidateEvaluation());
        model.addAttribute("batchId", storedCandidate.batchId());
        model.addAttribute("batchCreatedAtDisplay", storedCandidate.createdAtDisplay());
        model.addAttribute("shortlistCount", storedCandidate.shortlistCount());
        model.addAttribute("rankPosition", storedCandidate.rankPosition());
        return "candidate-detail";
    }

    @GetMapping("/history/{batchId}/eliminated")
    public String eliminatedCandidates(@PathVariable Long batchId, Model model) {
        StoredScreeningBatchResult storedBatch = screeningHistoryService.findBatch(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Screening batch not found"));
        java.util.List<StoredEliminatedCandidate> eliminatedCandidates = screeningHistoryService.findEliminatedCandidates(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Screening batch not found"));

        model.addAttribute("batchId", storedBatch.batchId());
        model.addAttribute("batchCreatedAtDisplay", storedBatch.createdAtDisplay());
        model.addAttribute("eliminatedCandidates", eliminatedCandidates);
        return "eliminated";
    }

    @GetMapping("/history/{batchId}/candidates/{rankPosition}/report")
    public String candidateReport(@PathVariable Long batchId,
                                  @PathVariable int rankPosition,
                                  org.springframework.ui.Model model) {
        StoredCandidateDetail storedCandidate = screeningHistoryService.findCandidate(batchId, rankPosition)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found"));

        StoredScreeningBatchResult storedBatch = screeningHistoryService.findBatch(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Screening batch not found"));

        CandidateEvaluation evaluation = storedCandidate.candidateEvaluation();
        String jdText = storedBatch.screeningResult().jobDescriptionProfile().originalText();

        CandidateReportNarrative narrative = candidateReportNarrativeService.generate(
                new CandidateReportNarrativeService.CandidateReportNarrativeRequest(
                        evaluation.candidateProfile().candidateName(),
                        evaluation.candidateProfile().extractedText(),
                        jdText,
                        storedBatch.sectorDisplay(),
                        storedBatch.scoringMode() != null ? storedBatch.scoringMode() : "heuristic",
                        evaluation.score()
                ));

        model.addAttribute("candidateEvaluation", evaluation);
        model.addAttribute("narrative", narrative);
        model.addAttribute("batchId", storedCandidate.batchId());
        model.addAttribute("batchCreatedAtDisplay", storedCandidate.createdAtDisplay());
        model.addAttribute("shortlistCount", storedCandidate.shortlistCount());
        model.addAttribute("rankPosition", storedCandidate.rankPosition());
        model.addAttribute("generatedAt", java.time.format.DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(java.time.ZoneId.systemDefault())
                .format(java.time.Instant.now()));
        return "candidate-report";
    }

    @GetMapping("/history/{batchId}/report")
    public String screeningReport(@PathVariable Long batchId, Model model) {
        StoredScreeningBatchResult storedBatch = screeningHistoryService.findBatch(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Screening batch not found"));
        List<StoredEliminatedCandidate> eliminatedCandidates = screeningHistoryService.findEliminatedCandidates(batchId)
                .orElse(List.of());

        List<CandidateEvaluation> shortlisted = storedBatch.screeningResult().shortlistedCandidates();
        List<CandidateEvaluation> rejected = storedBatch.screeningResult().candidateEvaluations().stream()
                .filter(e -> !e.shortlisted())
                .toList();

        int totalSubmitted = storedBatch.totalCvsReceived();
        int totalAnalysed  = storedBatch.candidatesScored();
        int totalEliminated = eliminatedCandidates.size();
        int parseFailures  = Math.max(0, totalSubmitted - totalAnalysed - totalEliminated);

        ReportNarrative narrative = reportNarrativeService.generate(
                new ReportNarrativeService.ReportNarrativeRequest(
                        storedBatch.screeningResult().jobDescriptionProfile().originalText(),
                        storedBatch.sectorDisplay(),
                        storedBatch.scoringMode() != null ? storedBatch.scoringMode() : "heuristic",
                        totalSubmitted,
                        totalAnalysed,
                        shortlisted.size(),
                        rejected.size(),
                        totalEliminated,
                        shortlisted,
                        rejected,
                        eliminatedCandidates
                ));

        model.addAttribute("batch", storedBatch);
        model.addAttribute("screeningResult", storedBatch.screeningResult());
        model.addAttribute("shortlistedCandidates", shortlisted);
        model.addAttribute("rejectedCandidates", rejected);
        model.addAttribute("eliminatedCandidates", eliminatedCandidates);
        model.addAttribute("narrative", narrative);
        model.addAttribute("totalSubmitted", totalSubmitted);
        model.addAttribute("totalAnalysed", totalAnalysed);
        model.addAttribute("totalShortlisted", shortlisted.size());
        model.addAttribute("totalRejected", rejected.size());
        model.addAttribute("totalEliminated", totalEliminated);
        model.addAttribute("parseFailures", parseFailures);
        model.addAttribute("noSelection", shortlisted.isEmpty());
        model.addAttribute("generatedAt", java.time.format.DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(java.time.ZoneId.systemDefault())
                .format(java.time.Instant.now()));
        return "report";
    }
}
