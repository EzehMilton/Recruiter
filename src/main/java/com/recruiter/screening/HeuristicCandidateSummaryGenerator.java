package com.recruiter.screening;

import com.recruiter.domain.CandidateProfile;
import com.recruiter.domain.JobDescriptionProfile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class HeuristicCandidateSummaryGenerator implements CandidateSummaryGenerator {

    @Override
    public String generate(JobDescriptionProfile jobDescriptionProfile,
                           CandidateProfile candidateProfile,
                           CandidateScoreDetails scoreDetails) {
        List<String> parts = new ArrayList<>();

        boolean hasAnyRequirements = !jobDescriptionProfile.requiredSkills().isEmpty()
                || !jobDescriptionProfile.preferredSkills().isEmpty()
                || !jobDescriptionProfile.domainKeywords().isEmpty();

        if (!hasAnyRequirements) {
            parts.add("No structured job requirements were detected, so the score stays conservative.");
        }

        // Per-category weighted reasoning
        for (CategoryScore cat : scoreDetails.categoryBreakdown()) {
            if (cat.effectiveWeight() == 0) {
                continue;
            }
            String detail = detailForCategory(cat.label(), scoreDetails, candidateProfile);
            parts.add(cat.label() + " [w" + cat.effectiveWeight() + "]: "
                    + formatScore(cat.score()) + "% -> "
                    + formatScore(cat.contribution()) + " pts."
                    + (detail.isEmpty() ? "" : " " + detail));
        }

        // Gaps
        if (!scoreDetails.missingRequiredSkills().isEmpty()) {
            List<String> gaps = scoreDetails.missingRequiredSkills().stream().limit(4).toList();
            parts.add("Gaps: " + String.join(", ", gaps) + ".");
        }

        parts.add("Weighted total: " + formatScore(scoreDetails.totalScore()) + "/100.");
        return String.join(" ", parts);
    }

    private String detailForCategory(String label, CandidateScoreDetails scoreDetails,
                                      CandidateProfile candidateProfile) {
        return switch (label) {
            case "Required Skills" -> formatMatched(scoreDetails.matchedRequiredSkills());
            case "Preferred Skills" -> formatMatched(scoreDetails.matchedPreferredSkills());
            case "Experience" -> candidateProfile.yearsOfExperience() != null
                    ? "(" + candidateProfile.yearsOfExperience() + " yrs)"
                    : "(could not estimate)";
            case "Domain Relevance" -> scoreDetails.matchedDomainKeywords().isEmpty()
                    ? "(weak match)"
                    : "(" + String.join(", ", scoreDetails.matchedDomainKeywords()) + ")";
            case "Qualifications" -> formatMatched(scoreDetails.matchedQualifications());
            case "Soft Skills" -> formatMatched(scoreDetails.matchedSoftSkills());
            default -> "";
        };
    }

    private String formatMatched(List<String> matched) {
        if (matched.isEmpty()) {
            return "";
        }
        return "(" + String.join(", ", matched) + ")";
    }

    private String formatScore(double score) {
        if (score == Math.floor(score)) {
            return String.valueOf((int) score);
        }
        return String.valueOf(score);
    }
}
