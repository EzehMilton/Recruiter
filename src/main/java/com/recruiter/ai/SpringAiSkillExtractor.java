package com.recruiter.ai;

import com.recruiter.domain.ScreeningPackage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;

public class SpringAiSkillExtractor implements AiSkillExtractor {

    private static final String SYSTEM_PROMPT = """
            Extract 10-15 key skills, qualifications, and domain-specific terms from this job description.
            Return only terms a recruiter would search for in a CV.
            Be specific to the domain, not generic soft skills.
            Return JSON only with a single `skills` array.
    """;

    private final ChatClient chatClient;
    private final AiModelSelectionService aiModelSelectionService;

    public SpringAiSkillExtractor(ChatClient.Builder chatClientBuilder,
                                  AiModelSelectionService aiModelSelectionService) {
        this.chatClient = chatClientBuilder.build();
        this.aiModelSelectionService = aiModelSelectionService;
    }

    @Override
    public AiResult<ExtractedJobSkills> extract(String jobDescriptionText, ScreeningPackage screeningPackage) {
        return AiResponseSupport.toAiResult(chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .options(OpenAiChatOptions.builder()
                        .model(aiModelSelectionService.screeningModel(screeningPackage))
                        .maxCompletionTokens(250)
                        .build())
                .user(jobDescriptionText)
                .call()
                .responseEntity(ExtractedJobSkills.class));
    }
}
