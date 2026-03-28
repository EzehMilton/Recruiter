package com.recruiter.screening;

import com.recruiter.config.ScoringWeightsProperties;
import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.CandidateProfile;
import com.recruiter.domain.JobDescriptionProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CandidateScoringService {

    private final TextProfileHeuristicsService heuristicsService;
    private final JobDescriptionProfileExtractor jobDescriptionProfileExtractor;
    private final CandidateSummaryService candidateSummaryService;
    private final CandidateEvaluationFactory candidateEvaluationFactory;
    private final ScoringWeightsProperties weights;

    public CandidateEvaluation evaluate(String jobDescriptionText, CandidateProfile candidateProfile) {
        return evaluate(jobDescriptionProfileExtractor.extract(jobDescriptionText), candidateProfile);
    }

    public CandidateEvaluation evaluate(JobDescriptionProfile jobDescriptionProfile, CandidateProfile candidateProfile) {
        CandidateScoreDetails scoreDetails = score(jobDescriptionProfile, candidateProfile);
        String summary = candidateSummaryService.generate(jobDescriptionProfile, candidateProfile, scoreDetails);
        return candidateEvaluationFactory.createSuccessful(candidateProfile, scoreDetails, summary);
    }

    public CandidateScoreDetails score(JobDescriptionProfile jobDescriptionProfile, CandidateProfile candidateProfile) {
        Set<String> candidateSkillsNorm = normalize(candidateProfile.skills());

        // Required skills
        Set<String> reqSkillsNorm = normalize(jobDescriptionProfile.requiredSkills());
        Set<String> matchedReqNorm = intersect(candidateSkillsNorm, reqSkillsNorm);
        List<String> matchedRequiredSkills = preserveLabels(jobDescriptionProfile.requiredSkills(), matchedReqNorm);
        List<String> missingRequiredSkills = preserveMissingLabels(jobDescriptionProfile.requiredSkills(), matchedReqNorm);
        double requiredSkillsRaw = ratio(matchedReqNorm.size(), reqSkillsNorm.size());

        // Preferred skills
        Set<String> prefSkillsNorm = normalize(jobDescriptionProfile.preferredSkills());
        Set<String> matchedPrefNorm = intersect(candidateSkillsNorm, prefSkillsNorm);
        List<String> matchedPreferredSkills = preserveLabels(jobDescriptionProfile.preferredSkills(), matchedPrefNorm);
        double preferredSkillsRaw = ratio(matchedPrefNorm.size(), prefSkillsNorm.size());

        // Experience
        double experienceRaw = calculateExperienceRatio(
                jobDescriptionProfile.yearsOfExperience(),
                candidateProfile.yearsOfExperience());

        // Domain relevance
        Set<String> jobDomainNorm = normalize(jobDescriptionProfile.domainKeywords());
        Set<String> candidateKeywords = heuristicsService.extractKeywords(candidateProfile.extractedText());
        Set<String> matchedDomainNorm = intersect(candidateKeywords, jobDomainNorm);
        Set<String> matchedDomainKeywords = limitKeywords(matchedDomainNorm);
        double domainRaw = ratio(matchedDomainNorm.size(), Math.min(jobDomainNorm.size(), 10));

        // Qualifications
        Set<String> jobQualsNorm = normalize(jobDescriptionProfile.qualifications());
        Set<String> candidateQualsNorm = normalize(candidateProfile.qualifications());
        Set<String> matchedQualsNorm = intersect(candidateQualsNorm, jobQualsNorm);
        List<String> matchedQualifications = preserveLabels(jobDescriptionProfile.qualifications(), matchedQualsNorm);
        double qualificationsRaw = ratio(matchedQualsNorm.size(), jobQualsNorm.size());

        // Soft skills
        Set<String> jobSoftNorm = normalize(jobDescriptionProfile.softSkills());
        Set<String> candidateSoftNorm = normalize(candidateProfile.softSkills());
        Set<String> matchedSoftNorm = intersect(candidateSoftNorm, jobSoftNorm);
        List<String> matchedSoftSkills = preserveLabels(jobDescriptionProfile.softSkills(), matchedSoftNorm);
        double softSkillsRaw = ratio(matchedSoftNorm.size(), jobSoftNorm.size());

        List<CategoryScore> categoryBreakdown = buildCategoryBreakdown(
                requiredSkillsRaw, preferredSkillsRaw, experienceRaw,
                domainRaw, qualificationsRaw, softSkillsRaw,
                jobDescriptionProfile);

        double totalScore = categoryBreakdown.stream()
                .mapToDouble(CategoryScore::contribution)
                .sum();

        return new CandidateScoreDetails(
                roundToSingleDecimal(clamp(totalScore, 0.0, 100.0)),
                roundToSingleDecimal(requiredSkillsRaw * 100.0),
                roundToSingleDecimal(preferredSkillsRaw * 100.0),
                roundToSingleDecimal(experienceRaw * 100.0),
                roundToSingleDecimal(domainRaw * 100.0),
                roundToSingleDecimal(qualificationsRaw * 100.0),
                roundToSingleDecimal(softSkillsRaw * 100.0),
                categoryBreakdown,
                matchedRequiredSkills,
                missingRequiredSkills,
                matchedPreferredSkills,
                matchedQualifications,
                matchedSoftSkills,
                matchedDomainKeywords
        );
    }

    private List<CategoryScore> buildCategoryBreakdown(double reqSkills, double prefSkills,
                                                        double experience, double domain,
                                                        double qualifications, double softSkills,
                                                        JobDescriptionProfile profile) {
        int effectiveReqWeight = profile.requiredSkills().isEmpty() ? 0 : weights.getRequiredSkills();
        int effectivePrefWeight = profile.preferredSkills().isEmpty() ? 0 : weights.getPreferredSkills();
        int effectiveExpWeight = profile.yearsOfExperience() == null || profile.yearsOfExperience() <= 0
                ? 0 : weights.getExperience();
        int effectiveDomainWeight = profile.domainKeywords().isEmpty() ? 0 : weights.getDomainRelevance();
        int effectiveQualWeight = profile.qualifications().isEmpty() ? 0 : weights.getQualifications();
        int effectiveSoftWeight = profile.softSkills().isEmpty() ? 0 : weights.getSoftSkills();

        int totalWeight = effectiveReqWeight + effectivePrefWeight + effectiveExpWeight
                + effectiveDomainWeight + effectiveQualWeight + effectiveSoftWeight;

        return List.of(
                buildCategory("Required Skills", reqSkills, effectiveReqWeight, totalWeight),
                buildCategory("Preferred Skills", prefSkills, effectivePrefWeight, totalWeight),
                buildCategory("Experience", experience, effectiveExpWeight, totalWeight),
                buildCategory("Domain Relevance", domain, effectiveDomainWeight, totalWeight),
                buildCategory("Qualifications", qualifications, effectiveQualWeight, totalWeight),
                buildCategory("Soft Skills", softSkills, effectiveSoftWeight, totalWeight)
        );
    }

    private CategoryScore buildCategory(String label, double rawRatio, int effectiveWeight, int totalWeight) {
        double contribution = totalWeight > 0
                ? roundToSingleDecimal((rawRatio * effectiveWeight / totalWeight) * 100.0)
                : 0.0;
        return new CategoryScore(label, roundToSingleDecimal(rawRatio * 100.0), effectiveWeight, contribution);
    }

    private double ratio(int matched, int total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.min((double) matched / total, 1.0);
    }

    private double calculateExperienceRatio(Integer requiredYears, Integer candidateYears) {
        if (requiredYears == null || requiredYears <= 0 || candidateYears == null || candidateYears < 0) {
            return 0.0;
        }
        return Math.min(candidateYears / (double) requiredYears, 1.0);
    }

    private Set<String> intersect(Set<String> a, Set<String> b) {
        Set<String> result = new LinkedHashSet<>(a);
        result.retainAll(b);
        return result;
    }

    private List<String> preserveLabels(List<String> originals, Set<String> matchedNormalized) {
        return originals.stream()
                .filter(label -> matchedNormalized.contains(label.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private List<String> preserveMissingLabels(List<String> originals, Set<String> matchedNormalized) {
        return originals.stream()
                .filter(label -> !matchedNormalized.contains(label.toLowerCase(Locale.ROOT)))
                .toList();
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
}
