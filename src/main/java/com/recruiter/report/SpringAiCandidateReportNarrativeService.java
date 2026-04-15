package com.recruiter.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

public class SpringAiCandidateReportNarrativeService implements CandidateReportNarrativeService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiCandidateReportNarrativeService.class);

    private static final String SYSTEM_PROMPT = """
            You are a senior recruitment consultant writing a confidential candidate assessment report.
            Write in clear, professional British English. Be objective, evidence-based, and specific —
            reference actual information from the CV and job description provided.
            Do NOT invent or assume details not present in the input.
            Return JSON only — no prose outside the JSON, no markdown fences.

            Your output must be a JSON object with exactly five fields:
            - "candidateSummary": 3–4 sentences giving a concise professional profile of the candidate
              based solely on their CV. Include their background, level of experience, and key career highlights.
            - "strengths": an array of 3–5 strings, each a specific strength relevant to the role,
              drawn from evidence in the CV matched against the job description.
            - "weaknesses": an array of 2–4 strings, each describing a gap, risk, or area of concern
              relative to the role requirements. If genuinely no gaps exist, return an empty array.
            - "roleFitSummary": 2–3 sentences summarising the overall fit of the candidate for this
              specific role, referencing the score and key matching/missing criteria.
            - "interviewQuestions": an array of exactly 5 objects, each with:
                - "question": a specific, open-ended first-interview question tailored to this candidate and role
                - "answerGuide": 1–2 sentences describing what a strong answer would include
                - "followUpQuestions": an array of 2 follow-up probing questions for that topic
            """;

    private final ChatClient chatClient;

    public SpringAiCandidateReportNarrativeService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public CandidateReportNarrative generate(CandidateReportNarrativeRequest request) {
        try {
            String userMessage = buildUserMessage(request);
            return chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userMessage)
                    .call()
                    .entity(CandidateReportNarrative.class);
        } catch (Exception e) {
            log.warn("AI candidate report narrative generation failed for {}, using fallback: {}",
                    request.candidateName(), e.getMessage());
            return new FallbackCandidateReportNarrativeService().generate(request);
        }
    }

    private String buildUserMessage(CandidateReportNarrativeRequest req) {
        return "CANDIDATE NAME: " + req.candidateName() + "\n" +
               "MATCH SCORE: " + Math.round(req.score()) + "/100\n" +
               "SECTOR: " + req.sector() + "\n" +
               "SCORING METHOD: " + req.scoringMode() + "\n\n" +
               "JOB DESCRIPTION:\n" + truncate(req.jobDescriptionText(), 1200) + "\n\n" +
               "CANDIDATE CV:\n" + truncate(req.cvText(), 2500);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
