package com.recruiter.web;

import com.recruiter.config.RecruitmentProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice(assignableTypes = HomeController.class)
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final RecruitmentProperties properties;

    @ExceptionHandler(Exception.class)
    public String handleUnexpectedException(Exception ex, Model model) {
        log.error("Unexpected screening request failure", ex);
        populateModel(model);
        model.addAttribute("errorMessage",
                "The screening request could not be completed due to an unexpected error. Please try again.");
        return "index";
    }

    private void populateModel(Model model) {
        ScreeningForm form = new ScreeningForm();
        form.setShortlistCount(properties.getShortlistCount());

        model.addAttribute("screeningForm", form);
        model.addAttribute("maxCandidates", properties.getEffectiveUploadProcessingCap());
        model.addAttribute("maxWords", properties.getMaxJobDescriptionWords());
    }
}
