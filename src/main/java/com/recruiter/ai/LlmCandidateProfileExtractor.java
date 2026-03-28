package com.recruiter.ai;

import com.recruiter.config.RecruitmentAiProperties;
import com.recruiter.document.ExtractedDocument;
import com.recruiter.domain.CandidateProfile;
import com.recruiter.prompt.PromptLoader;
import com.recruiter.screening.CandidateProfileExtractor;
import com.recruiter.screening.HeuristicCandidateProfileExtractor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Primary
@RequiredArgsConstructor
public class LlmCandidateProfileExtractor implements CandidateProfileExtractor {

    private static final Logger log = LoggerFactory.getLogger(LlmCandidateProfileExtractor.class);
    private static final String PROMPT_FILENAME = "extract-candidate-profile.txt";

    private final AiClient aiClient;
    private final PromptLoader promptLoader;
    private final HeuristicCandidateProfileExtractor heuristicExtractor;
    private final RecruitmentAiProperties aiProperties;
    private final AiInputFormatter aiInputFormatter;

    @Override
    public CandidateProfile extract(ExtractedDocument extractedDocument) {
        if (extractedDocument == null) {
            return heuristicExtractor.extract(new ExtractedDocument("", ""));
        }
        if (extractedDocument.text() == null || extractedDocument.text().isBlank()) {
            return heuristicExtractor.extract(extractedDocument);
        }

        try {
            AiCandidateProfileResponse response = aiClient.generateStructuredObject(
                    new AiStructuredRequest(
                            "candidate_profile_extraction",
                            promptLoader.load(PROMPT_FILENAME),
                            aiInputFormatter.toJson(candidateInput(extractedDocument)),
                            AiResponseSchemas.candidateProfile()
                    ),
                    AiCandidateProfileResponse.class
            );

            return new CandidateProfile(
                    response.candidateName(),
                    extractedDocument.originalFilename(),
                    extractedDocument.text(),
                    safeList(response.skills()),
                    safeList(response.qualifications()),
                    safeList(response.softSkills()),
                    response.yearsOfExperience()
            );
        } catch (AiClientException ex) {
            if (!aiProperties.isFallbackToHeuristics()) {
                throw ex;
            }
            log.warn("AI candidate profile extraction failed for '{}', falling back to heuristics: {}",
                    extractedDocument.originalFilename(), ex.getMessage());
            return heuristicExtractor.extract(extractedDocument);
        }
    }

    private Map<String, Object> candidateInput(ExtractedDocument extractedDocument) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("filename", extractedDocument.originalFilename());
        input.put("cvText", extractedDocument.text());
        return input;
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private record AiCandidateProfileResponse(
            String candidateName,
            List<String> skills,
            List<String> qualifications,
            List<String> softSkills,
            Integer yearsOfExperience
    ) {
    }
}
