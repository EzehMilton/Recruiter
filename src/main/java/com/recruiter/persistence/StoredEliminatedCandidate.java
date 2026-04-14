package com.recruiter.persistence;

import java.util.List;

public record StoredEliminatedCandidate(
        String candidateName,
        String candidateFilename,
        double preFilterScore,
        List<String> matchedSkills,
        String scoreLabel,
        String eliminationReason
) {

    public String matchedSkillsDisplay() {
        return matchedSkills == null || matchedSkills.isEmpty()
                ? "No direct skill match recorded"
                : String.join(", ", matchedSkills);
    }

    public String scoreLabelDisplay() {
        return scoreLabel == null || scoreLabel.isBlank() ? "Recorded score" : scoreLabel;
    }

    public String eliminationReasonDisplay() {
        return eliminationReason == null || eliminationReason.isBlank()
                ? "Removed from the final results."
                : eliminationReason;
    }
}
