package com.recruiter.domain;

import java.util.List;
import java.util.Objects;

public record CandidateProfile(
        String candidateName,
        String sourceFilename,
        String extractedText,
        List<String> skills,
        List<String> qualifications,
        List<String> softSkills,
        Integer yearsOfExperience
) {

    public CandidateProfile {
        candidateName = normalize(candidateName);
        sourceFilename = normalize(sourceFilename);
        extractedText = Objects.requireNonNullElse(extractedText, "").trim();
        skills = List.copyOf(Objects.requireNonNullElse(skills, List.of()));
        qualifications = List.copyOf(Objects.requireNonNullElse(qualifications, List.of()));
        softSkills = List.copyOf(Objects.requireNonNullElse(softSkills, List.of()));
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }
}
