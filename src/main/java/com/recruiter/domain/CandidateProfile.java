package com.recruiter.domain;

import java.util.List;
import java.util.Objects;

public record CandidateProfile(
        String candidateName,
        String sourceFilename,
        String extractedText,
        List<String> extractedSkills,
        Integer yearsOfExperience
) {

    public CandidateProfile {
        candidateName = normalize(candidateName);
        sourceFilename = normalize(sourceFilename);
        extractedText = Objects.requireNonNullElse(extractedText, "").trim();
        extractedSkills = List.copyOf(Objects.requireNonNullElse(extractedSkills, List.of()));
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }
}
