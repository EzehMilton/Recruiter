package com.recruiter.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruiter.domain.ScreeningPackage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;

public class SpringAiFitAssessmentAiService implements FitAssessmentAiService {

    private static final String SYSTEM_PROMPT = """
            You are a structured fit assessor for recruitment screening.

            You will receive two JSON objects: a structured job description profile and a structured
            candidate profile. Both were extracted earlier — treat them as your only source of truth.
            Return JSON only — no prose, no markdown fences.

            Your task: compare the candidate profile against the job profile and produce a bounded
            fit assessment.

            Rules:
            - Heavily weight essentialFit. A candidate missing multiple essential requirements
              should not be rated STRONG_MATCH regardless of other strengths.
            - For each dimension (essentialFit, desirableFit, experienceFit, domainFit,
              credentialsFit), assign a level: STRONG, PARTIAL, WEAK, or NONE.
            - Provide a brief rationale for each dimension judgement citing specific evidence
              from the candidate profile or specific gaps against the job profile.
            - Set overallRecommendation to one of: STRONG_MATCH, POSSIBLE_MATCH, WEAK_MATCH,
              or NOT_RECOMMENDED. This must be consistent with the dimension judgements —
              do not rate STRONG_MATCH if essentialFit is WEAK or NONE.
            - Set confidence to HIGH if both profiles have high extraction quality and the
              comparison is clear, MEDIUM if some ambiguity exists, LOW if either profile is
              sparse or unclear.
            - List concrete topStrengths — specific capabilities or evidence that match the job
              requirements. Do not use vague praise.
            - List concrete topGaps — specific job requirements the candidate does not demonstrably
              meet. Do not invent gaps for items not in the job profile.
            - List interviewProbeAreas — specific topics a recruiter should explore in an interview
              to resolve ambiguities or verify claims.
            - Write a recruiterFacingExplanation of 2-4 sentences in plain English that a recruiter
              can scan in under 10 seconds. Summarise the fit, the key strength, and the key risk.
            - Do NOT reward prestige language, verbosity, or generic claims by themselves.
              Only credit capabilities backed by evidence in the candidate profile.
            - Do NOT produce a free-form numeric score. The bounded judgements are the output.
    """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final AiModelSelectionService aiModelSelectionService;

    public SpringAiFitAssessmentAiService(ChatClient.Builder chatClientBuilder,
                                          ObjectMapper objectMapper,
                                          AiModelSelectionService aiModelSelectionService) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.aiModelSelectionService = aiModelSelectionService;
    }

    @Override
    public AiResult<AiFitAssessment> assess(AiJobDescriptionProfile job, AiCandidateProfile candidate,
                                            ScreeningPackage screeningPackage) {
        return assess(job, candidate, SYSTEM_PROMPT, screeningPackage);
    }

    @Override
    public AiResult<AiFitAssessment> assess(AiJobDescriptionProfile job, AiCandidateProfile candidate,
                                            String systemPrompt,
                                            ScreeningPackage screeningPackage) {
        String userMessage = buildUserMessage(job, candidate);
        return AiResponseSupport.toAiResult(chatClient.prompt()
                .system(systemPrompt)
                .options(OpenAiChatOptions.builder()
                        .model(aiModelSelectionService.screeningModel(screeningPackage))
                        .build())
                .user(userMessage)
                .call()
                .responseEntity(AiFitAssessment.class));
    }

    private String buildUserMessage(AiJobDescriptionProfile job, AiCandidateProfile candidate) {
        try {
            return "Job Profile:\n" + objectMapper.writeValueAsString(job)
                    + "\n\nCandidate Profile:\n" + objectMapper.writeValueAsString(candidate);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize AI profiles for fit assessment", e);
        }
    }
}
