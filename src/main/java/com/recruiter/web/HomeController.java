package com.recruiter.web;

import com.recruiter.config.RecruitmentProperties;
import com.recruiter.domain.ScreeningRunResult;
import com.recruiter.screening.CandidateScreeningFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    private final RecruitmentProperties properties;
    private final CandidateScreeningFacade candidateScreeningFacade;
    private final ScreeningFormValidator screeningFormValidator;

    @InitBinder("screeningForm")
    public void initBinder(WebDataBinder binder) {
        binder.addValidators(screeningFormValidator);
    }

    @GetMapping("/")
    public String home(Model model) {
        ScreeningForm form = new ScreeningForm();
        form.setShortlistCount(properties.getShortlistCount());
        form.setMinimumShortlistScore(properties.getMinimumShortlistScore());
        model.addAttribute("screeningForm", form);
        addFormConstants(model);
        return "index";
    }

    @PostMapping("/analyse")
    public String analyse(@Valid @ModelAttribute ScreeningForm screeningForm,
                          BindingResult bindingResult,
                          Model model) {
        int uploadedFileCount = countUploadedFiles(screeningForm.getCvFiles());
        log.info("Screening request started: uploadedFiles={}, requestedShortlistCount={}",
                uploadedFileCount, screeningForm.getShortlistCount());

        if (bindingResult.hasErrors()) {
            log.warn("Screening request validation failed: uploadedFiles={}, validationErrors={}",
                    uploadedFileCount, bindingResult.getErrorCount());
            addFormConstants(model);
            return "index";
        }

        ScreeningRunResult screeningRunResult = candidateScreeningFacade.screen(
                screeningForm.getJobDescription(),
                screeningForm.getShortlistCount(),
                screeningForm.getMinimumShortlistScore(),
                screeningForm.getScoringMode(),
                screeningForm.getCvFiles()
        );
        var screeningResult = screeningRunResult.screeningResult();

        model.addAttribute("screeningResult", screeningResult);
        model.addAttribute("batchId", screeningRunResult.batchId());
        model.addAttribute("shortlistCount", screeningRunResult.shortlistCount());
        model.addAttribute("scoringMode", screeningRunResult.effectiveScoringMode().name());
        model.addAttribute("totalCvsReceived", screeningRunResult.totalCvsReceived());
        model.addAttribute("candidatesScored", screeningRunResult.candidatesScored());
        model.addAttribute("wasReduced", screeningRunResult.wasReduced());
        model.addAttribute("successMessage",
                buildSuccessMessage(screeningRunResult));
        log.info("Screening request completed: candidatesProcessed={}, shortlisted={}",
                screeningResult.candidateEvaluations().size(),
                screeningResult.shortlistedCandidates().size());
        return "results";
    }

    private String buildSuccessMessage(ScreeningRunResult result) {
        var screeningResult = result.screeningResult();
        int shortlisted = screeningResult.shortlistedCandidates().size();
        if (result.wasReduced()) {
            return "Received " + result.totalCvsReceived() + " CVs. "
                    + "Reduced to the top " + result.candidatesScored()
                    + " candidates using a fast first-pass relevance filter before full analysis. "
                    + shortlisted + " candidate(s) shortlisted.";
        }
        return "Analysed " + result.candidatesScored()
                + " CV(s) and selected " + shortlisted + " shortlisted candidate(s).";
    }

    private void addFormConstants(Model model) {
        model.addAttribute("maxCandidates", properties.getEffectiveUploadProcessingCap());
        model.addAttribute("maxWords", properties.getMaxJobDescriptionWords());
    }

    private int countUploadedFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return 0;
        }
        return (int) files.stream()
                .filter(file -> !file.isEmpty())
                .count();
    }
}
