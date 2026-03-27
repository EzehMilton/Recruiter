package com.recruiter.domain;

import java.util.List;
import java.util.Objects;

public record JobDescriptionProfile(
        String originalText,
        List<String> extractedSkills,
        List<String> requiredKeywords,
        Integer yearsOfExperience
) {

    public JobDescriptionProfile {
        originalText = Objects.requireNonNullElse(originalText, "").trim();
        extractedSkills = List.copyOf(Objects.requireNonNullElse(extractedSkills, List.of()));
        requiredKeywords = List.copyOf(Objects.requireNonNullElse(requiredKeywords, List.of()));
    }
}
