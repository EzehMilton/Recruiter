package com.recruiter.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruiter.config.RecruitmentAiProperties;
import com.recruiter.domain.CandidateProfile;
import com.recruiter.domain.JobDescriptionProfile;
import com.recruiter.prompt.PromptLoader;
import com.recruiter.screening.CandidateScoreDetails;
import com.recruiter.screening.CategoryScore;
import com.recruiter.screening.HeuristicCandidateSummaryGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LlmCandidateSummaryGeneratorTest {

    @Test
    void usesAiSummaryWhenAvailable() {
        LlmCandidateSummaryGenerator generator = new LlmCandidateSummaryGenerator(
                new StubAiClient(new SummaryResponse("Strong Java and Spring fit with relevant AWS exposure.")),
                new PromptLoader(new DefaultResourceLoader()),
                new HeuristicCandidateSummaryGenerator(),
                aiProperties(),
                new AiInputFormatter(new ObjectMapper())
        );

        String summary = generator.generate(jobProfile(), candidateProfile(), scoreDetails());

        assertThat(summary).isEqualTo("Strong Java and Spring fit with relevant AWS exposure.");
    }

    @Test
    void inputSentToAiContainsOnlyScoredEvidenceAndNoRawText() {
        CapturingAiClient capturingClient = new CapturingAiClient(
                new SummaryResponse("Summary.")
        );
        LlmCandidateSummaryGenerator generator = new LlmCandidateSummaryGenerator(
                capturingClient,
                new PromptLoader(new DefaultResourceLoader()),
                new HeuristicCandidateSummaryGenerator(),
                aiProperties(),
                new AiInputFormatter(new ObjectMapper())
        );

        generator.generate(jobProfile(), candidateProfile(), scoreDetails());

        String input = capturingClient.capturedRequest.input();

        // Must contain scored evidence
        assertThat(input).contains("matchedRequiredSkills");
        assertThat(input).contains("missingRequiredSkills");
        assertThat(input).contains("categoryBreakdown");
        assertThat(input).contains("totalScore");

        // Must NOT contain raw text fields that could cause hallucination
        assertThat(input).doesNotContain("originalText");
        assertThat(input).doesNotContain("extractedText");
        assertThat(input).doesNotContain("Senior Java engineer"); // job originalText value
        assertThat(input).doesNotContain("jane-doe.pdf");         // candidate filename
    }

    @Test
    void buildSummaryInputExcludesCandidateSkillsAndQualifications() {
        LlmCandidateSummaryGenerator generator = new LlmCandidateSummaryGenerator(
                new StubAiClient(new SummaryResponse("ok")),
                new PromptLoader(new DefaultResourceLoader()),
                new HeuristicCandidateSummaryGenerator(),
                aiProperties(),
                new AiInputFormatter(new ObjectMapper())
        );

        Map<String, Object> input = generator.buildSummaryInput(jobProfile(), candidateProfile(), scoreDetails());

        @SuppressWarnings("unchecked")
        Map<String, Object> candidate = (Map<String, Object>) input.get("candidate");
        assertThat(candidate).containsOnlyKeys("candidateName", "yearsOfExperience");
        assertThat(candidate).doesNotContainKey("skills");
        assertThat(candidate).doesNotContainKey("extractedText");

        assertThat(input).doesNotContainKey("jobDescriptionProfile");
        assertThat(input).containsKey("jobRequirements");
        assertThat(input).containsKey("scoredEvidence");
    }

    @Test
    void fallsBackToHeuristicSummaryWhenAiFails() {
        LlmCandidateSummaryGenerator generator = new LlmCandidateSummaryGenerator(
                new FailingAiClient(),
                new PromptLoader(new DefaultResourceLoader()),
                new HeuristicCandidateSummaryGenerator(),
                aiProperties(),
                new AiInputFormatter(new ObjectMapper())
        );

        String summary = generator.generate(jobProfile(), candidateProfile(), scoreDetails());

        assertThat(summary).contains("Weighted total:");
        assertThat(summary).contains("Required Skills");
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

    private JobDescriptionProfile jobProfile() {
        return new JobDescriptionProfile(
                "Senior Java engineer",
                List.of("Java", "Spring Boot", "AWS"),
                List.of(),
                List.of(),
                List.of(),
                List.of("backend", "services"),
                5
        );
    }

    private CandidateProfile candidateProfile() {
        return new CandidateProfile(
                "Jane Doe",
                "jane-doe.pdf",
                "Jane Doe Java Spring Boot AWS 6 years experience",
                List.of("Java", "Spring Boot", "AWS"),
                List.of(),
                List.of(),
                6
        );
    }

    private CandidateScoreDetails scoreDetails() {
        List<CategoryScore> breakdown = List.of(
                new CategoryScore("Required Skills", 100.0, 30, 30.0),
                new CategoryScore("Preferred Skills", 0.0, 0, 0.0),
                new CategoryScore("Experience", 100.0, 20, 20.0),
                new CategoryScore("Domain Relevance", 80.0, 15, 12.0),
                new CategoryScore("Qualifications", 0.0, 0, 0.0),
                new CategoryScore("Soft Skills", 0.0, 0, 0.0)
        );
        return new CandidateScoreDetails(
                88.0,
                100.0, 0.0, 100.0, 80.0, 0.0, 0.0,
                breakdown,
                List.of("Java", "Spring Boot", "AWS"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Set.of("backend", "services")
        );
    }

    private record SummaryResponse(String summary) {
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

    private static final class CapturingAiClient implements AiClient {

        private final Object response;
        AiStructuredRequest capturedRequest;

        private CapturingAiClient(Object response) {
            this.response = response;
        }

        @Override
        public <T> T generateStructuredObject(AiStructuredRequest request, Class<T> responseType) {
            this.capturedRequest = request;
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
