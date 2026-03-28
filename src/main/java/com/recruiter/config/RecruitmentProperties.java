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
    @Max(20)
    private int maxCandidates = 20;

    @Min(1)
    @Max(20)
    private int maxParallelCandidateEvaluations = 4;

    @Min(1)
    private long maxFileSizeBytes = 5 * 1024 * 1024; // 5 MB

    public void setShortlistCount(int shortlistCount) {
        this.shortlistCount = shortlistCount;
    }

    public void setMaxJobDescriptionWords(int maxJobDescriptionWords) {
        this.maxJobDescriptionWords = maxJobDescriptionWords;
    }

    public void setMaxCandidates(int maxCandidates) {
        this.maxCandidates = maxCandidates;
    }

    public void setMaxParallelCandidateEvaluations(int maxParallelCandidateEvaluations) {
        this.maxParallelCandidateEvaluations = maxParallelCandidateEvaluations;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }
}
