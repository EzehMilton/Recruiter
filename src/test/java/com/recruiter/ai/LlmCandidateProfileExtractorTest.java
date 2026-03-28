package com.recruiter.ai;

import com.recruiter.config.RecruitmentAiProperties;
import com.recruiter.document.ExtractedDocument;
import com.recruiter.domain.CandidateProfile;
import com.recruiter.prompt.PromptLoader;
import com.recruiter.screening.HeuristicCandidateProfileExtractor;
import com.recruiter.screening.TextProfileHeuristicsService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmCandidateProfileExtractorTest {

    @Test
    void usesAiStructuredOutputWhenAvailable() {
        LlmCandidateProfileExtractor extractor = new LlmCandidateProfileExtractor(
                new StubAiClient(new CandidateProfileResponse(
                        "Jane Doe", List.of("Java", "AWS"),
                        List.of("Bachelor's"), List.of("Communication"), 6)),
                new PromptLoader(new DefaultResourceLoader()),
                new HeuristicCandidateProfileExtractor(new TextProfileHeuristicsService()),
                aiProperties(),
                new AiInputFormatter(new com.fasterxml.jackson.databind.ObjectMapper())
        );

        CandidateProfile profile = extractor.extract(new ExtractedDocument(
                "jane-doe.pdf",
                "Jane Doe\nJava AWS\n6 years experience"
        ));

        assertThat(profile.candidateName()).isEqualTo("Jane Doe");
        assertThat(profile.sourceFilename()).isEqualTo("jane-doe.pdf");
        assertThat(profile.skills()).containsExactly("Java", "AWS");
        assertThat(profile.qualifications()).containsExactly("Bachelor's");
        assertThat(profile.softSkills()).containsExactly("Communication");
        assertThat(profile.yearsOfExperience()).isEqualTo(6);
    }

    @Test
    void fallsBackToHeuristicsWhenAiFails() {
        LlmCandidateProfileExtractor extractor = new LlmCandidateProfileExtractor(
                new FailingAiClient(),
                new PromptLoader(new DefaultResourceLoader()),
                new HeuristicCandidateProfileExtractor(new TextProfileHeuristicsService()),
                aiProperties(),
                new AiInputFormatter(new com.fasterxml.jackson.databind.ObjectMapper())
        );

        CandidateProfile profile = extractor.extract(new ExtractedDocument(
                "jane-doe.pdf",
                "Jane Doe\nJava AWS\n6 years experience"
        ));

        assertThat(profile.candidateName()).isEqualTo("Jane Doe");
        assertThat(profile.skills()).contains("Java", "AWS");
        assertThat(profile.yearsOfExperience()).isEqualTo(6);
    }

    private RecruitmentAiProperties aiProperties() {
        RecruitmentAiProperties properties = new RecruitmentAiProperties();
        properties.setEnabled(true);
        properties.setProvider("openai");
        properties.setFallbackToHeuristics(true);
        properties.getOpenai().setApiKey("test-key");
        properties.getOpenai().setModel("test-model");
        return properties;
    }

    private record CandidateProfileResponse(
            String candidateName, List<String> skills,
            List<String> qualifications, List<String> softSkills,
            Integer yearsOfExperience) {
    }

    private static final class StubAiClient implements AiClient {

        private final Object response;

        private StubAiClient(Object response) {
            this.response = response;
        }

        @Override
        public <T> T generateStructuredObject(AiStructuredRequest request, Class<T> responseType) {
            return responseType.cast(response);
        }
    }

    private static final class FailingAiClient implements AiClient {

        @Override
        public <T> T generateStructuredObject(AiStructuredRequest request, Class<T> responseType) {
            throw new AiClientException("simulated AI failure");
        }
    }
}
