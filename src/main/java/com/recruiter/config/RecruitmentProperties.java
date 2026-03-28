package com.recruiter.config;

import jakarta.validation.constraints.Max;
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

    @Min(0)
    @Max(100)
    private double minimumShortlistScore = 75.0;

    @Min(1)
    private long maxFileSizeBytes = 5 * 1024 * 1024; // 5 MB

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

    public void setMinimumShortlistScore(double minimumShortlistScore) {
        this.minimumShortlistScore = minimumShortlistScore;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }
}
