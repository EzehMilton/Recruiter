package com.recruiter.service;

import com.recruiter.domain.JobDescriptionProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HeuristicJobDescriptionProfileFactory implements JobDescriptionProfileFactory {

    private final TextProfileHeuristicsService heuristicsService;

    @Override
    public JobDescriptionProfile create(String jobDescriptionText) {
        return new JobDescriptionProfile(
                jobDescriptionText,
                heuristicsService.extractSkills(jobDescriptionText),
                heuristicsService.extractRequiredKeywords(jobDescriptionText),
                heuristicsService.extractYearsOfExperience(jobDescriptionText)
        );
    }
}
