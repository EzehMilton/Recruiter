package com.recruiter.ai;

import com.recruiter.config.RecruitmentAiProperties;
import com.recruiter.domain.CandidateProfile;
import com.recruiter.domain.JobDescriptionProfile;
import com.recruiter.prompt.PromptLoader;
import com.recruiter.screening.CandidateScoreDetails;
import com.recruiter.screening.CandidateSummaryGenerator;
import com.recruiter.screening.HeuristicCandidateSummaryGenerator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Primary
@RequiredArgsConstructor
public class LlmCandidateSummaryGenerator implements CandidateSummaryGenerator {

    private static final Logger log = LoggerFactory.getLogger(LlmCandidateSummaryGenerator.class);
    private static final String PROMPT_FILENAME = "summarize-candidate.txt";

    private final AiClient aiClient;
    private final PromptLoader promptLoader;
    private final HeuristicCandidateSummaryGenerator heuristicSummaryGenerator;
    private final RecruitmentAiProperties aiProperties;
    private final AiInputFormatter aiInputFormatter;

    @Override
    public String generate(JobDescriptionProfile jobDescriptionProfile,
                           CandidateProfile candidateProfile,
                           CandidateScoreDetails scoreDetails) {
        try {
            AiCandidateSummaryResponse response = aiClient.generateStructuredObject(
                    new AiStructuredRequest(
                            "candidate_summary_generation",
                            promptLoader.load(PROMPT_FILENAME),
                            aiInputFormatter.toJson(buildSummaryInput(jobDescriptionProfile, candidateProfile, scoreDetails)),
                            AiResponseSchemas.candidateSummary()
                    ),
                    AiCandidateSummaryResponse.class
            );

            if (response.summary() == null || response.summary().isBlank()) {
                throw new AiClientException("AI summary response was blank");
            }
            return response.summary().trim();
        } catch (AiClientException ex) {
            if (!aiProperties.isFallbackToHeuristics()) {
                throw ex;
            }
            log.warn("AI candidate summary generation failed, falling back to heuristics: {}", ex.getMessage());
            return heuristicSummaryGenerator.generate(jobDescriptionProfile, candidateProfile, scoreDetails);
        }
    }

    Map<String, Object> buildSummaryInput(JobDescriptionProfile jobDescriptionProfile,
                                          CandidateProfile candidateProfile,
                                          CandidateScoreDetails scoreDetails) {
        Map<String, Object> input = new LinkedHashMap<>();

        // Job requirements — structured fields only, no raw text
        Map<String, Object> jobRequirements = new LinkedHashMap<>();
        jobRequirements.put("requiredSkills", jobDescriptionProfile.requiredSkills());
        jobRequirements.put("preferredSkills", jobDescriptionProfile.preferredSkills());
        jobRequirements.put("qualifications", jobDescriptionProfile.qualifications());
        jobRequirements.put("softSkills", jobDescriptionProfile.softSkills());
        jobRequirements.put("domainKeywords", jobDescriptionProfile.domainKeywords());
        jobRequirements.put("yearsOfExperience", jobDescriptionProfile.yearsOfExperience());
        input.put("jobRequirements", jobRequirements);

        // Candidate — only name and years; matched evidence comes from scoreDetails
        Map<String, Object> candidateInput = new LinkedHashMap<>();
        candidateInput.put("candidateName", candidateProfile.candidateName());
        candidateInput.put("yearsOfExperience", candidateProfile.yearsOfExperience());
        input.put("candidate", candidateInput);

        // Scored evidence — the single source of truth the LLM should reason about
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("totalScore", scoreDetails.totalScore());
        evidence.put("categoryBreakdown", scoreDetails.categoryBreakdown());
        evidence.put("matchedRequiredSkills", scoreDetails.matchedRequiredSkills());
        evidence.put("missingRequiredSkills", scoreDetails.missingRequiredSkills());
        evidence.put("matchedPreferredSkills", scoreDetails.matchedPreferredSkills());
        evidence.put("matchedQualifications", scoreDetails.matchedQualifications());
        evidence.put("matchedSoftSkills", scoreDetails.matchedSoftSkills());
        evidence.put("matchedDomainKeywords", scoreDetails.matchedDomainKeywords());
        input.put("scoredEvidence", evidence);

        return input;
    }

    private record AiCandidateSummaryResponse(String summary) {
    }
}
