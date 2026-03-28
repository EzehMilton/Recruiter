package com.recruiter.web;

import com.recruiter.persistence.ScreeningHistoryService;
import com.recruiter.persistence.StoredCandidateDetail;
import com.recruiter.persistence.StoredScreeningBatchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequiredArgsConstructor
public class HistoryController {

    private final ScreeningHistoryService screeningHistoryService;

    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("batches", screeningHistoryService.listHistory());
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
}
