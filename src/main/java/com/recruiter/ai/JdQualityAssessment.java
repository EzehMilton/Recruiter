package com.recruiter.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JdQualityAssessment(
        Integer score,
        String rating,
        String confidence,
        String processingDecision,
        String jobTitle,
        String summary,
        List<String> clarityIssues,
        List<String> missingRequirements,
        List<String> overloadedAreas,
        List<String> contradictions,
        List<String> weakLanguage,
        List<String> mustHaveSkills,
        List<String> niceToHaveSkills,
        List<String> risks,
        List<String> recommendations,
        String improvedVersion,
        ClientMessage clientMessage
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClientMessage(String subject, String body) {}

    public boolean isWeak() {
        return score == null || score < 60;
    }

    public static JdQualityAssessment alwaysProceed() {
        return new JdQualityAssessment(
                100, "STRONG", "HIGH", "PROCEED",
                "Unknown", "",
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                "", new ClientMessage("", "")
        );
    }
}
