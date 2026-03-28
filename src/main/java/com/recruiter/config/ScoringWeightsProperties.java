package com.recruiter.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Validated
@ConfigurationProperties(prefix = "recruitment.scoring.weights")
public class ScoringWeightsProperties {

    @Min(0)
    private int requiredSkills = 30;

    @Min(0)
    private int preferredSkills = 15;

    @Min(0)
    private int experience = 20;

    @Min(0)
    private int domainRelevance = 15;

    @Min(0)
    private int qualifications = 10;

    @Min(0)
    private int softSkills = 10;

    public void setRequiredSkills(int requiredSkills) {
        this.requiredSkills = requiredSkills;
    }

    public void setPreferredSkills(int preferredSkills) {
        this.preferredSkills = preferredSkills;
    }

    public void setExperience(int experience) {
        this.experience = experience;
    }

    public void setDomainRelevance(int domainRelevance) {
        this.domainRelevance = domainRelevance;
    }

    public void setQualifications(int qualifications) {
        this.qualifications = qualifications;
    }

    public void setSoftSkills(int softSkills) {
        this.softSkills = softSkills;
    }

    public int totalWeight() {
        return requiredSkills + preferredSkills + experience + domainRelevance + qualifications + softSkills;
    }
}
