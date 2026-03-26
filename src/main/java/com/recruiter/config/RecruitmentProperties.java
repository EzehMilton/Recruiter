package com.recruiter.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "recruitment")
public class RecruitmentProperties {

    @Min(1)
    private int shortlistCount = 3;

    @Min(1)
    @Max(20)
    private int maxCandidates = 20;

    @Min(1)
    private long maxFileSizeBytes = 5 * 1024 * 1024; // 5 MB

    public int getShortlistCount() {
        return shortlistCount;
    }

    public void setShortlistCount(int shortlistCount) {
        this.shortlistCount = shortlistCount;
    }

    public int getMaxCandidates() {
        return maxCandidates;
    }

    public void setMaxCandidates(int maxCandidates) {
        this.maxCandidates = maxCandidates;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }
}