package com.recruiter.web;

import com.recruiter.config.RecruitmentProperties;
import com.recruiter.document.UploadedCvValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
@RequiredArgsConstructor
public class ScreeningFormValidator implements Validator {

    private final RecruitmentProperties properties;
    private final UploadedCvValidationService uploadedCvValidationService;

    @Override
    public boolean supports(Class<?> clazz) {
        return ScreeningForm.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        ScreeningForm form = (ScreeningForm) target;
        validateJobDescriptionWordCount(form, errors);
        validateShortlistCountMax(form, errors);
        validateCvFiles(form, errors);
    }

    private void validateJobDescriptionWordCount(ScreeningForm form, Errors errors) {
        if (errors.hasFieldErrors("jobDescription")) {
            return;
        }
        int wordCount = form.getJobDescription().trim().split("\\s+").length;
        int maxJobDescriptionWords = properties.getMaxJobDescriptionWords();
        if (wordCount > maxJobDescriptionWords) {
            errors.rejectValue("jobDescription", "WordCount",
                    "Job description must not exceed " + maxJobDescriptionWords + " words (currently " + wordCount + ")");
        }
    }

    private void validateShortlistCountMax(ScreeningForm form, Errors errors) {
        if (errors.hasFieldErrors("shortlistCount") || form.getShortlistCount() == null) {
            return;
        }
        if (form.getShortlistCount() > properties.getMaxCandidates()) {
            errors.rejectValue("shortlistCount", "Max",
                    "Shortlist count cannot exceed the maximum of " + properties.getMaxCandidates() + " candidates");
        }
    }

    private void validateCvFiles(ScreeningForm form, Errors errors) {
        if (errors.hasFieldErrors("cvFiles")) {
            return;
        }

        uploadedCvValidationService.validate(form.getCvFiles())
                .forEach(message -> errors.rejectValue("cvFiles", "Invalid", message));
    }
}
