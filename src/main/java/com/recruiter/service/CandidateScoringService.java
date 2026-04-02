package com.recruiter.service;

import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.CandidateProfile;
import com.recruiter.domain.CandidateScoreBreakdown;
import com.recruiter.domain.JobDescriptionProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CandidateScoringService {

    private final TextProfileHeuristicsService heuristicsService;
    private final JobDescriptionProfileFactory jobDescriptionProfileFactory;

    public CandidateEvaluation evaluate(String jobDescriptionText, CandidateProfile candidateProfile) {
        return evaluate(jobDescriptionProfileFactory.create(jobDescriptionText), candidateProfile);
    }

    public CandidateEvaluation evaluate(JobDescriptionProfile jobDescriptionProfile, CandidateProfile candidateProfile) {
        ScoreBreakdown breakdown = score(jobDescriptionProfile, candidateProfile);
        String summary = buildSummary(jobDescriptionProfile, candidateProfile, breakdown);

        return new CandidateEvaluation(
                candidateProfile,
                breakdown.totalScore(),
                new CandidateScoreBreakdown(
                        breakdown.skillScore(),
                        breakdown.keywordScore(),
                        breakdown.experienceScore()
                ),
                "heuristic",
                summary,
                false,
                "", List.of(), List.of(), List.of()
        );
    }

    private ScoreBreakdown score(JobDescriptionProfile jobDescriptionProfile, CandidateProfile candidateProfile) {
        Set<String> jobSkills = normalize(jobDescriptionProfile.extractedSkills());
        Set<String> candidateSkills = normalize(candidateProfile.extractedSkills());
        Set<String> jobKeywords = normalize(jobDescriptionProfile.requiredKeywords());
        Set<String> candidateKeywords = heuristicsService.extractKeywords(candidateProfile.extractedText());
        Set<String> matchedSkills = new LinkedHashSet<>(candidateSkills);
        matchedSkills.retainAll(jobSkills);
        Set<String> keywordMatches = new LinkedHashSet<>(candidateKeywords);
        keywordMatches.retainAll(jobKeywords);

        List<String> matchedSkillLabels = jobDescriptionProfile.extractedSkills().stream()
                .filter(skill -> matchedSkills.contains(skill.toLowerCase(Locale.ROOT)))
                .toList();
        List<String> missingSkillLabels = jobDescriptionProfile.extractedSkills().stream()
                .filter(skill -> !matchedSkills.contains(skill.toLowerCase(Locale.ROOT)))
                .toList();

        RequirementClassification classification = heuristicsService.classifyRequirements(
                jobDescriptionProfile.originalText(), jobDescriptionProfile.extractedSkills());

        Set<String> matchedEssential = normalize(classification.essentialSkills());
        matchedEssential.retainAll(candidateSkills);
        Set<String> allEssential = normalize(classification.essentialSkills());
        int missingEssentialCount = allEssential.size() - matchedEssential.size();
        boolean hasEssentials = !allEssential.isEmpty();

        double broaderMax = hasEssentials ? 25.0 : 65.0;
        double essentialFit = calculateEssentialFit(allEssential, matchedEssential);
        double broaderSkillFit = calculateBroaderSkillFit(jobSkills, matchedSkills, broaderMax);
        double keywordScore = calculateKeywordScore(jobKeywords, keywordMatches);
        double experienceScore = calculateExperienceScore(
                jobDescriptionProfile.yearsOfExperience(),
                candidateProfile.yearsOfExperience());
        double gapPenalty = calculateGapPenalty(allEssential, missingEssentialCount);

        double skillScore = essentialFit + broaderSkillFit + gapPenalty;
        double total = roundToSingleDecimal(clamp(skillScore + keywordScore + experienceScore, 0.0, 100.0));
        return new ScoreBreakdown(total, matchedSkillLabels, missingSkillLabels, limitKeywords(keywordMatches),
                roundToSingleDecimal(clamp(skillScore, 0.0, 100.0)), keywordScore, experienceScore,
                classification, matchedEssential.size());
    }

    private double calculateEssentialFit(Set<String> allEssential, Set<String> matchedEssential) {
        if (allEssential.isEmpty()) {
            return 0.0;
        }
        return (matchedEssential.size() * 40.0) / allEssential.size();
    }

    private double calculateBroaderSkillFit(Set<String> jobSkills, Set<String> matchedSkills, double maxPoints) {
        if (jobSkills.isEmpty()) {
            return matchedSkills.isEmpty() ? 0.0 : maxPoints * 0.5;
        }
        return (matchedSkills.size() * maxPoints) / jobSkills.size();
    }

    private double calculateKeywordScore(Set<String> jobKeywords, Set<String> keywordMatches) {
        if (jobKeywords.isEmpty() || keywordMatches.isEmpty()) {
            return 0.0;
        }

        double denominator = Math.min(jobKeywords.size(), 10);
        return (keywordMatches.size() * 15.0) / denominator;
    }

    private double calculateExperienceScore(Integer requiredYears, Integer candidateYears) {
        if (requiredYears == null || requiredYears <= 0 || candidateYears == null || candidateYears < 0) {
            return 0.0;
        }

        double ratio = Math.min(candidateYears / (double) requiredYears, 1.0);
        return ratio * 10.0;
    }

    private double calculateGapPenalty(Set<String> allEssential, int missingEssentialCount) {
        if (allEssential.isEmpty()) {
            return 0.0;
        }
        if (missingEssentialCount == allEssential.size() && allEssential.size() >= 2) {
            return -10.0;
        }
        if (missingEssentialCount > allEssential.size() / 2.0) {
            return -5.0;
        }
        return 0.0;
    }

    private String buildSummary(JobDescriptionProfile jobDescriptionProfile,
                                CandidateProfile candidateProfile,
                                ScoreBreakdown breakdown) {
        List<String> parts = new ArrayList<>();

        RequirementClassification classification = breakdown.classification();
        if (classification != null && !classification.essentialSkills().isEmpty()) {
            int total = classification.essentialSkills().size();
            int matched = breakdown.matchedEssentialCount();
            parts.add("Essential requirements met: " + matched + "/" + total + ".");
        }

        if (jobDescriptionProfile.extractedSkills().isEmpty() && jobDescriptionProfile.requiredKeywords().isEmpty()) {
            parts.add("No structured job requirements were detected, so the score stays conservative.");
        } else if (breakdown.matchedSkills().isEmpty()) {
            parts.add("No clear job-skill matches were found in the extracted CV text.");
        } else {
            parts.add("Matched " + breakdown.matchedSkills().size()
                    + " important terms: " + String.join(", ", breakdown.matchedSkills()) + ".");
        }

        if (!breakdown.missingSkills().isEmpty()) {
            List<String> missingEssentialLabels = classification != null
                    ? classification.essentialSkills().stream()
                        .filter(breakdown.missingSkills()::contains)
                        .toList()
                    : List.of();
            List<String> otherMissing = breakdown.missingSkills().stream()
                    .filter(skill -> !missingEssentialLabels.contains(skill))
                    .toList();
            List<String> highlightedGaps = new ArrayList<>(missingEssentialLabels);
            for (String skill : otherMissing) {
                if (highlightedGaps.size() >= 3) {
                    break;
                }
                highlightedGaps.add(skill);
            }
            parts.add("Weak match on " + String.join(", ", highlightedGaps) + ".");
        }

        if (!breakdown.keywordMatches().isEmpty()) {
            parts.add("Also matched required keywords: " + String.join(", ", breakdown.keywordMatches()) + ".");
        } else if (!jobDescriptionProfile.requiredKeywords().isEmpty()) {
            parts.add("Weak match on required keywords from the job description.");
        }

        if (candidateProfile.yearsOfExperience() != null) {
            parts.add("Estimated experience: " + candidateProfile.yearsOfExperience() + " years.");
        } else {
            parts.add("Years of experience could not be estimated.");
        }

        parts.add("Deterministic score: " + breakdown.totalScore() + "/100.");
        return String.join(" ", parts);
    }

    private Set<String> limitKeywords(Set<String> keywordMatches) {
        return keywordMatches.stream()
                .sorted(Comparator.naturalOrder())
                .limit(6)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> normalize(List<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            normalized.add(value.toLowerCase(Locale.ROOT));
        }
        return normalized;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double roundToSingleDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record ScoreBreakdown(
            double totalScore,
            List<String> matchedSkills,
            List<String> missingSkills,
            Set<String> keywordMatches,
            double skillScore,
            double keywordScore,
            double experienceScore,
            RequirementClassification classification,
            int matchedEssentialCount
    ) {
    }
}
