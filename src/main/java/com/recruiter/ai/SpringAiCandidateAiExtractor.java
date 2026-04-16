package com.recruiter.ai;

import com.recruiter.domain.ScreeningPackage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;

public class SpringAiCandidateAiExtractor implements CandidateAiExtractor {

    private static final String SYSTEM_PROMPT = """
            You are a structured data extractor for candidate CVs / resumes.

            Your task: extract demonstrated evidence from the CV text provided by the user.
            Return JSON only — no prose, no markdown fences.

            You must handle CVs for any profession — technical, scientific, manual, healthcare,
            education, hospitality, logistics, legal, creative, business, cleaning, warehouse,
            care roles, and others.

            Rules:
            - Do NOT invent achievements, certifications, years of experience, or skills that
              are not stated or clearly evidenced in the text.
            - For each demonstrated capability and responsibility, assign an evidenceStrength:
              STRONG if the CV gives specific, concrete evidence (metrics, named projects, durations),
              MODERATE if mentioned with some context but lacking specifics,
              WEAK if only listed as a keyword or claim with no supporting evidence.
            - Populate supportingEvidence with a brief quote or paraphrase from the CV that
              justifies the strength rating.
            - Capture tools, methods, or systems mentioned (e.g. "Python", "forklift", "SAP",
              "Adobe InDesign").
            - Capture qualifications or certifications exactly as stated (e.g. "BSc Computer Science",
              "CSCS card", "Level 3 NVQ").
            - Capture domain experience areas (e.g. "e-commerce", "NHS", "construction").
            - Capture soft skills only when the CV provides evidence, not just keywords. Include
              the evidence in the string (e.g. "Led a team of 8 warehouse operatives" rather than
              just "Leadership").
            - Capture work context signals (e.g. "shift-based", "remote", "travel 50%",
              "security cleared").
            - Capture constraints or eligibility signals (e.g. "requires visa sponsorship",
              "has UK driving licence", "available immediately").
            - Set extractionQuality to HIGH if the CV is well-structured and detailed, MEDIUM if
              partially clear, LOW if very sparse or hard to parse.
            - Record ambiguities or missing data (e.g. "no dates on employment history",
              "skills listed without context", "years of experience unclear").
            - Set estimatedYearsOfRelevantExperience based on employment dates if available.
              If not calculable, set to null.
            - Do NOT score or rank the candidate. You are only extracting evidence.
    """;

    private final ChatClient chatClient;
    private final AiModelSelectionService aiModelSelectionService;

    public SpringAiCandidateAiExtractor(ChatClient.Builder chatClientBuilder,
                                        AiModelSelectionService aiModelSelectionService) {
        this.chatClient = chatClientBuilder.build();
        this.aiModelSelectionService = aiModelSelectionService;
    }

    @Override
    public AiResult<AiCandidateProfile> extract(String cvText, ScreeningPackage screeningPackage) {
        return AiResponseSupport.toAiResult(chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .options(OpenAiChatOptions.builder()
                        .model(aiModelSelectionService.screeningModel(screeningPackage))
                        .build())
                .user(cvText)
                .call()
                .responseEntity(AiCandidateProfile.class));
    }
}
