package com.recruiter.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers AI extraction and assessment beans only when a real OpenAI API key is configured.
 * When OPENAI_API_KEY is absent the property falls back to "disabled" and these beans are not created,
 * so the application starts normally using only heuristic screening.
 */
@Configuration
@ConditionalOnExpression("'${spring.ai.openai.api-key:disabled}' != 'disabled'")
public class AiServiceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiServiceConfiguration.class);

    @Bean
    JobDescriptionAiExtractor jobDescriptionAiExtractor(ChatClient.Builder chatClientBuilder) {
        log.info("AI services enabled: registering JobDescriptionAiExtractor (prompt {})", AiPromptVersions.JOB_EXTRACTOR);
        return new SpringAiJobDescriptionAiExtractor(chatClientBuilder);
    }

    @Bean
    AiSkillExtractor aiSkillExtractor(ChatClient.Builder chatClientBuilder) {
        log.info("AI services enabled: registering AiSkillExtractor (prompt {})", AiPromptVersions.JOB_SKILL_EXTRACTOR);
        return new SpringAiSkillExtractor(chatClientBuilder);
    }

    @Bean
    CandidateAiExtractor candidateAiExtractor(ChatClient.Builder chatClientBuilder) {
        log.info("AI services enabled: registering CandidateAiExtractor (prompt {})", AiPromptVersions.CANDIDATE_EXTRACTOR);
        return new SpringAiCandidateAiExtractor(chatClientBuilder);
    }

    @Bean
    FitAssessmentAiService fitAssessmentAiService(ChatClient.Builder chatClientBuilder) {
        log.info("AI services enabled: registering FitAssessmentAiService (prompt {})", AiPromptVersions.FIT_ASSESSOR);
        return new SpringAiFitAssessmentAiService(chatClientBuilder, new com.fasterxml.jackson.databind.ObjectMapper());
    }
}
