package com.recruiter.web;

import com.recruiter.config.RecruitmentProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@ControllerAdvice(assignableTypes = HomeController.class)
@RequiredArgsConstructor
public class MultipartUploadExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(MultipartUploadExceptionHandler.class);

    private final RecruitmentProperties properties;

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex, Model model) {
        log.warn("Screening upload failed: uploaded file exceeded configured max size", ex);
        populateModel(model);
        model.addAttribute("errorMessage",
                "One or more uploaded CVs exceed the maximum size of "
                        + humanReadableSize(properties.getMaxFileSizeBytes()) + ".");
        return "index";
    }

    @ExceptionHandler(MultipartException.class)
    public String handleMultipartException(MultipartException ex, Model model) {
        log.warn("Screening upload failed: multipart request could not be processed", ex);
        populateModel(model);
        model.addAttribute("errorMessage",
                "The uploaded CVs could not be processed. Check that each file is a non-empty PDF within the allowed size limit.");
        return "index";
    }

    private void populateModel(Model model) {
        ScreeningForm form = new ScreeningForm();
        form.setShortlistCount(properties.getShortlistCount());

        model.addAttribute("screeningForm", form);
        model.addAttribute("maxCandidates", properties.getMaxCandidates());
        model.addAttribute("maxWords", properties.getMaxJobDescriptionWords());
    }

    private String humanReadableSize(long bytes) {
        long megabyte = 1024 * 1024;
        long kilobyte = 1024;

        if (bytes % megabyte == 0) {
            return (bytes / megabyte) + " MB";
        }
        if (bytes % kilobyte == 0) {
            return (bytes / kilobyte) + " KB";
        }
        return bytes + " bytes";
    }
}
