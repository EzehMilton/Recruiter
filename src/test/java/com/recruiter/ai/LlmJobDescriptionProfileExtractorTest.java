package com.recruiter.ai;

import com.recruiter.config.RecruitmentAiProperties;
import com.recruiter.domain.JobDescriptionProfile;
import com.recruiter.prompt.PromptLoader;
import com.recruiter.screening.HeuristicJobDescriptionProfileExtractor;
import com.recruiter.screening.TextProfileHeuristicsService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmJobDescriptionProfileExtractorTest {

    private final RecruitmentAiProperties aiProperties = aiProperties(true);
    private final LlmJobDescriptionProfileExtractor extractor = new LlmJobDescriptionProfileExtractor(
            new StubAiClient(new JobProfileResponse(
                    List.of("Java", "Spring Boot"), List.of("Docker"),
                    List.of("Bachelor's"), List.of("Communication"),
                    List.of("backend", "apis"), 5)),
            new PromptLoader(new DefaultResourceLoader()),
            new HeuristicJobDescriptionProfileExtractor(new TextProfileHeuristicsService()),
            aiProperties
    );

    @Test
    void usesAiStructuredOutputWhenAvailable() {
        JobDescriptionProfile profile = extractor.extract("Senior Java engineer with Spring Boot. 5 years required.");

        assertThat(profile.requiredSkills()).containsExactly("Java", "Spring Boot");
        assertThat(profile.preferredSkills()).containsExactly("Docker");
        assertThat(profile.qualifications()).containsExactly("Bachelor's");
        assertThat(profile.softSkills()).containsExactly("Communication");
        assertThat(profile.domainKeywords()).containsExactly("backend", "apis");
        assertThat(profile.yearsOfExperience()).isEqualTo(5);
    }

    @Test
    void fallsBackToHeuristicsWhenAiFails() {
        LlmJobDescriptionProfileExtractor failingExtractor = new LlmJobDescriptionProfileExtractor(
                new FailingAiClient(),
                new PromptLoader(new DefaultResourceLoader()),
                new HeuristicJobDescriptionProfileExtractor(new TextProfileHeuristicsService()),
                aiProperties(true)
        );

        JobDescriptionProfile profile = failingExtractor.extract("Senior Java engineer with Spring Boot. 5 years required.");

        assertThat(profile.requiredSkills()).contains("Java", "Spring Boot");
        assertThat(profile.yearsOfExperience()).isEqualTo(5);
    }

    private RecruitmentAiProperties aiProperties(boolean fallbackToHeuristics) {
        RecruitmentAiProperties properties = new RecruitmentAiProperties();
        properties.setEnabled(true);
        properties.setProvider("openai");
        properties.setFallbackToHeuristics(fallbackToHeuristics);
        properties.getOpenai().setApiKey("test-key");
        properties.getOpenai().setModel("test-model");
        return properties;
    }

    private record JobProfileResponse(
            List<String> requiredSkills, List<String> preferredSkills,
            List<String> qualifications, List<String> softSkills,
            List<String> domainKeywords, Integer yearsOfExperience) {
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
