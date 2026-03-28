package com.recruiter.screening;

import com.recruiter.domain.JobDescriptionProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HeuristicJobDescriptionProfileExtractor implements JobDescriptionProfileExtractor {

    private final TextProfileHeuristicsService heuristicsService;

    @Override
    public JobDescriptionProfile extract(String jobDescriptionText) {
        List<String> allSkills = heuristicsService.extractSkills(jobDescriptionText);
        List<String> preferredSkills = heuristicsService.extractPreferredSkills(jobDescriptionText);

        Set<String> preferredSet = new LinkedHashSet<>(preferredSkills);
        List<String> requiredSkills = new ArrayList<>();
        for (String skill : allSkills) {
            if (!preferredSet.contains(skill)) {
                requiredSkills.add(skill);
            }
        }

        return new JobDescriptionProfile(
                jobDescriptionText,
                requiredSkills,
                preferredSkills,
                heuristicsService.extractQualifications(jobDescriptionText),
                heuristicsService.extractSoftSkills(jobDescriptionText),
                heuristicsService.extractDomainKeywords(jobDescriptionText),
                heuristicsService.extractYearsOfExperience(jobDescriptionText)
        );
    }
}
