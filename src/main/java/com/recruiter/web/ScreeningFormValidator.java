package com.recruiter.web;

import com.recruiter.config.RecruitmentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ScreeningFormValidator implements Validator {

    static final int MAX_JOB_DESCRIPTION_WORDS = 1000;

    private final RecruitmentProperties properties;

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
        if (wordCount > MAX_JOB_DESCRIPTION_WORDS) {
            errors.rejectValue("jobDescription", "WordCount",
                    "Job description must not exceed " + MAX_JOB_DESCRIPTION_WORDS + " words (currently " + wordCount + ")");
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
        List<MultipartFile> files = form.getCvFiles();

        if (files == null || files.isEmpty() || files.stream().allMatch(MultipartFile::isEmpty)) {
            errors.rejectValue("cvFiles", "NotEmpty", "Please upload at least one CV");
            return;
        }

        long actualCount = files.stream().filter(f -> !f.isEmpty()).count();
        if (actualCount > properties.getMaxCandidates()) {
            errors.rejectValue("cvFiles", "MaxSize",
                    "You can upload at most " + properties.getMaxCandidates() + " CVs (you selected " + actualCount + ")");
        }
    }
}