package com.recruiter.screening;

import java.util.List;
import java.util.Set;

public record CandidateScoreDetails(
        double totalScore,
        double requiredSkillsScore,
        double preferredSkillsScore,
        double experienceScore,
        double domainRelevanceScore,
        double qualificationsScore,
        double softSkillsScore,
        List<CategoryScore> categoryBreakdown,
        List<String> matchedRequiredSkills,
        List<String> missingRequiredSkills,
        List<String> matchedPreferredSkills,
        List<String> matchedQualifications,
        List<String> matchedSoftSkills,
        Set<String> matchedDomainKeywords
) {
}
