package com.recruiter.web;

import com.recruiter.ai.Sector;
import com.recruiter.config.RecruitmentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

@Component
@RequiredArgsConstructor
public class HomePageModelSupport {

    private final RecruitmentProperties properties;

    public void populateFreshForm(Model model) {
        model.addAttribute("screeningForm", newScreeningForm());
        addFormConstants(model);
    }

    public void addFormConstants(Model model) {
        model.addAttribute("maxCandidates", properties.getEffectiveUploadProcessingCap());
        model.addAttribute("maxWords", properties.getMaxJobDescriptionWords());
        model.addAttribute("maxFileSizeLabel", humanReadableSize(properties.getMaxFileSizeBytes()));
        model.addAttribute("sectors", Sector.values());
    }

    public ScreeningForm newScreeningForm() {
        ScreeningForm form = new ScreeningForm();
        form.setShortlistCount(properties.getShortlistCount());
        form.setShortlistQuality(properties.getDefaultShortlistQuality());
        return form;
    }

    public String maxUploadSizeMessage() {
        return "One or more uploaded CVs exceed the maximum size of "
                + humanReadableSize(properties.getMaxFileSizeBytes()) + ".";
    }

    public String multipartUploadMessage() {
        return "The uploaded CVs could not be processed. Check that each file is a non-empty PDF within the allowed size limit.";
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
