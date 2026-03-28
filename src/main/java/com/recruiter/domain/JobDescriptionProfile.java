package com.recruiter.domain;

import java.util.List;
import java.util.Objects;

public record JobDescriptionProfile(
        String originalText,
        List<String> requiredSkills,
        List<String> preferredSkills,
        List<String> qualifications,
        List<String> softSkills,
        List<String> domainKeywords,
        Integer yearsOfExperience
) {

    public JobDescriptionProfile {
        originalText = Objects.requireNonNullElse(originalText, "").trim();
        requiredSkills = List.copyOf(Objects.requireNonNullElse(requiredSkills, List.of()));
        preferredSkills = List.copyOf(Objects.requireNonNullElse(preferredSkills, List.of()));
        qualifications = List.copyOf(Objects.requireNonNullElse(qualifications, List.of()));
        softSkills = List.copyOf(Objects.requireNonNullElse(softSkills, List.of()));
        domainKeywords = List.copyOf(Objects.requireNonNullElse(domainKeywords, List.of()));
    }
}
