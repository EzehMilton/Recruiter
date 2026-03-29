package com.recruiter.config;

import com.recruiter.domain.ShortlistQuality;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Validated
@ConfigurationProperties(prefix = "recruitment")
public class RecruitmentProperties {

    @Min(1)
    private int shortlistCount = 3;

    @Min(1)
    private int maxJobDescriptionWords = 1000;

    @Min(1)
    private int analysisCap = 20;

    private Integer uploadProcessingCap;

    private ShortlistQuality defaultShortlistQuality = ShortlistQuality.VERY_GOOD;

    @Min(1)
    private long maxFileSizeBytes = 5 * 1024 * 1024; // 5 MB

    private final AiCost aiCost = new AiCost();

    public void setShortlistCount(int shortlistCount) {
        this.shortlistCount = shortlistCount;
    }

    public void setMaxJobDescriptionWords(int maxJobDescriptionWords) {
        this.maxJobDescriptionWords = maxJobDescriptionWords;
    }

    public void setAnalysisCap(int analysisCap) {
        this.analysisCap = analysisCap;
    }

    public void setUploadProcessingCap(Integer uploadProcessingCap) {
        this.uploadProcessingCap = uploadProcessingCap;
    }

    public int getEffectiveUploadProcessingCap() {
        return uploadProcessingCap != null ? uploadProcessingCap : 500;
    }

    public void setDefaultShortlistQuality(ShortlistQuality defaultShortlistQuality) {
        this.defaultShortlistQuality = defaultShortlistQuality;
    }

    public double getMinimumShortlistScore() {
        return defaultShortlistQuality.getThreshold();
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Getter
    public static class AiCost {

        private double promptPricePerMillion = 0.15;
        private double completionPricePerMillion = 0.60;

        public void setPromptPricePerMillion(double promptPricePerMillion) {
            this.promptPricePerMillion = promptPricePerMillion;
        }

        public void setCompletionPricePerMillion(double completionPricePerMillion) {
            this.completionPricePerMillion = completionPricePerMillion;
        }
    }
}
