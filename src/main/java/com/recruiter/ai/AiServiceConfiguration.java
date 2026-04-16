package com.recruiter.ai;

import com.recruiter.report.CandidateReportNarrativeService;
import com.recruiter.report.ReportNarrativeService;
import com.recruiter.report.SpringAiCandidateReportNarrativeService;
import com.recruiter.report.SpringAiReportNarrativeService;
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
    JobDescriptionAiExtractor jobDescriptionAiExtractor(ChatClient.Builder chatClientBuilder,
                                                        AiModelSelectionService aiModelSelectionService) {
        log.info("AI services enabled: registering JobDescriptionAiExtractor (prompt {})", AiPromptVersions.JOB_EXTRACTOR);
        return new SpringAiJobDescriptionAiExtractor(chatClientBuilder, aiModelSelectionService);
    }

    @Bean
    AiSkillExtractor aiSkillExtractor(ChatClient.Builder chatClientBuilder,
                                      AiModelSelectionService aiModelSelectionService) {
        log.info("AI services enabled: registering AiSkillExtractor (prompt {})", AiPromptVersions.JOB_SKILL_EXTRACTOR);
        return new SpringAiSkillExtractor(chatClientBuilder, aiModelSelectionService);
    }

    @Bean
    CandidateAiExtractor candidateAiExtractor(ChatClient.Builder chatClientBuilder,
                                              AiModelSelectionService aiModelSelectionService) {
        log.info("AI services enabled: registering CandidateAiExtractor (prompt {})", AiPromptVersions.CANDIDATE_EXTRACTOR);
        return new SpringAiCandidateAiExtractor(chatClientBuilder, aiModelSelectionService);
    }

    @Bean
    FitAssessmentAiService fitAssessmentAiService(ChatClient.Builder chatClientBuilder,
                                                  AiModelSelectionService aiModelSelectionService) {
        log.info("AI services enabled: registering FitAssessmentAiService (prompt {})", AiPromptVersions.FIT_ASSESSOR);
        return new SpringAiFitAssessmentAiService(chatClientBuilder,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                aiModelSelectionService);
    }

    @Bean
    ReportNarrativeService reportNarrativeService(ChatClient.Builder chatClientBuilder,
                                                  AiModelSelectionService aiModelSelectionService) {
        log.info("AI services enabled: registering ReportNarrativeService");
        return new SpringAiReportNarrativeService(chatClientBuilder, aiModelSelectionService);
    }

    @Bean
    CandidateReportNarrativeService candidateReportNarrativeService(ChatClient.Builder chatClientBuilder,
                                                                    AiModelSelectionService aiModelSelectionService) {
        log.info("AI services enabled: registering CandidateReportNarrativeService");
        return new SpringAiCandidateReportNarrativeService(chatClientBuilder, aiModelSelectionService);
    }
}
