package com.recruiter.ai;

import java.util.List;

public record AiCandidateProfile(
        String candidateName,
        String headlineOrCurrentRole,
        String estimatedSeniorityLevel,
        Integer estimatedYearsOfRelevantExperience,
        List<EvidenceItem> demonstratedCapabilities,
        List<EvidenceItem> responsibilitiesPerformed,
        List<String> toolsMethodsOrSystems,
        List<String> qualificationsOrCertifications,
        List<String> domainExperience,
        List<String> softSkillsWithEvidence,
        List<String> workContextSignals,
        List<String> constraintsOrEligibilitySignals,
        ExtractionQuality extractionQuality,
        List<String> ambiguitiesOrMissingData
) {
}
