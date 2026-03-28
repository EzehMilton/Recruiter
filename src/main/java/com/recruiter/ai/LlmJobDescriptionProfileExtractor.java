package com.recruiter.ai;

import com.recruiter.config.RecruitmentAiProperties;
import com.recruiter.domain.JobDescriptionProfile;
import com.recruiter.prompt.PromptLoader;
import com.recruiter.screening.HeuristicJobDescriptionProfileExtractor;
import com.recruiter.screening.JobDescriptionProfileExtractor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Primary
@RequiredArgsConstructor
public class LlmJobDescriptionProfileExtractor implements JobDescriptionProfileExtractor {

    private static final Logger log = LoggerFactory.getLogger(LlmJobDescriptionProfileExtractor.class);
    private static final String PROMPT_FILENAME = "extract-job-profile.txt";

    private final AiClient aiClient;
    private final PromptLoader promptLoader;
    private final HeuristicJobDescriptionProfileExtractor heuristicExtractor;
    private final RecruitmentAiProperties aiProperties;

    @Override
    public JobDescriptionProfile extract(String jobDescriptionText) {
        if (jobDescriptionText == null || jobDescriptionText.isBlank()) {
            return heuristicExtractor.extract(jobDescriptionText);
        }

        try {
            AiJobDescriptionProfileResponse response = aiClient.generateStructuredObject(
                    new AiStructuredRequest(
                            "job_profile_extraction",
                            promptLoader.load(PROMPT_FILENAME),
                            jobDescriptionText,
                            AiResponseSchemas.jobDescriptionProfile()
                    ),
                    AiJobDescriptionProfileResponse.class
            );

            return new JobDescriptionProfile(
                    jobDescriptionText,
                    safeList(response.requiredSkills()),
                    safeList(response.preferredSkills()),
                    safeList(response.qualifications()),
                    safeList(response.softSkills()),
                    safeList(response.domainKeywords()),
                    response.yearsOfExperience()
            );
        } catch (AiClientException ex) {
            if (!aiProperties.isFallbackToHeuristics()) {
                throw ex;
            }
            log.warn("AI job profile extraction failed, falling back to heuristics: {}", ex.getMessage());
            return heuristicExtractor.extract(jobDescriptionText);
        }
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private record AiJobDescriptionProfileResponse(
            List<String> requiredSkills,
            List<String> preferredSkills,
            List<String> qualifications,
            List<String> softSkills,
            List<String> domainKeywords,
            Integer yearsOfExperience
    ) {
    }
}
