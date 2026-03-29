package com.recruiter.ai;

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

    public SpringAiSkillExtractor(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public AiResult<ExtractedJobSkills> extract(String jobDescriptionText) {
        return AiResponseSupport.toAiResult(chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .options(OpenAiChatOptions.builder()
                        .maxCompletionTokens(250)
                        .build())
                .user(jobDescriptionText)
                .call()
                .responseEntity(ExtractedJobSkills.class));
    }
}
