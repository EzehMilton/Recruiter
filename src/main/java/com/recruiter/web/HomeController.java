package com.recruiter.web;

import com.recruiter.config.RecruitmentProperties;
import com.recruiter.domain.ScreeningResult;
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

        ScreeningResult screeningResult = candidateScreeningFacade.screen(
                screeningForm.getJobDescription(),
                screeningForm.getShortlistCount(),
                screeningForm.getCvFiles()
        );

        model.addAttribute("screeningResult", screeningResult);
        model.addAttribute("successMessage",
                "Analysed " + screeningResult.candidateEvaluations().size()
                        + " CV(s) and selected "
                        + screeningResult.shortlistedCandidates().size() + " shortlisted candidate(s).");
        log.info("Screening request completed: candidatesProcessed={}, shortlisted={}",
                screeningResult.candidateEvaluations().size(),
                screeningResult.shortlistedCandidates().size());
        return "results";
    }

    private void addFormConstants(Model model) {
        model.addAttribute("maxCandidates", properties.getMaxCandidates());
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
