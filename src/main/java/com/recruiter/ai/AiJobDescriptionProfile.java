package com.recruiter.ai;

import java.util.List;

public record AiJobDescriptionProfile(
        String roleTitle,
        String roleFamily,
        String seniorityLevel,
        List<RequirementItem> essentialRequirements,
        List<RequirementItem> desirableRequirements,
        List<String> responsibilities,
        List<String> toolsMethodsOrSystems,
        List<String> qualificationsOrCertifications,
        List<String> domainContext,
        List<String> softSkills,
        List<String> workConditionsOrConstraints,
        String employmentType,
        String locationMode,
        ExtractionQuality extractionQuality,
        List<String> notesForRanking
) {
}
