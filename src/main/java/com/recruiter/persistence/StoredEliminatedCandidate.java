package com.recruiter.persistence;

import java.util.List;

public record StoredEliminatedCandidate(
        String candidateName,
        String candidateFilename,
        double preFilterScore,
        List<String> matchedSkills
) {

    public String matchedSkillsDisplay() {
        return matchedSkills == null || matchedSkills.isEmpty()
                ? "No direct skill match recorded"
                : String.join(", ", matchedSkills);
    }
}
