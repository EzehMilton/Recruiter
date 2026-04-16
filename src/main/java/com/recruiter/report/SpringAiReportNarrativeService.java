package com.recruiter.report;

import com.recruiter.ai.AiResponseSupport;
import com.recruiter.ai.AiResult;
import com.recruiter.ai.AiModelSelectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;

import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.persistence.StoredEliminatedCandidate;

import java.util.List;
import java.util.stream.Collectors;

public class SpringAiReportNarrativeService implements ReportNarrativeService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiReportNarrativeService.class);

    private static final String SYSTEM_PROMPT = """
            You are a professional recruitment consultant writing a client-facing screening report.
            Write in clear, professional British English. Be objective, concise, and factual.
            Do NOT invent details not present in the input. Do NOT use bullet points in executiveSummary
            or methodologyText — write in flowing prose paragraphs. Use bullet points only in nextSteps.
            Return JSON only — no prose, no markdown fences.

            Your output must be a JSON object with exactly three fields:
            - "executiveSummary": 3–4 sentences summarising the role requirement, the volume of
              applications reviewed, the shortlist outcome, and the overall talent pool quality.
            - "methodologyText": 2–3 sentences describing how candidates were selected, including
              the scoring approach used (AI or heuristic), what criteria were weighted, and how
              the shortlist threshold was applied.
            - "nextSteps": a concise bulleted list (use "• " prefix for each item) of 3–5 specific
              recommended actions for the client, such as interview scheduling, reference checking,
              or re-advertising if no candidates were shortlisted.
    """;

    private final ChatClient chatClient;
    private final AiModelSelectionService aiModelSelectionService;

    public SpringAiReportNarrativeService(ChatClient.Builder chatClientBuilder,
                                          AiModelSelectionService aiModelSelectionService) {
        this.chatClient = chatClientBuilder.build();
        this.aiModelSelectionService = aiModelSelectionService;
    }

    @Override
    public AiResult<ReportNarrative> generate(ReportNarrativeRequest request) {
        try {
            String userMessage = buildUserMessage(request);
            String model = aiModelSelectionService.reportingModel(
                    request.screeningPackage(), request.scoringMode());
            log.info("AI reporting model selected: reportType=screening_summary, package={}, scoringMode={}, model={}",
                    request.screeningPackage(), request.scoringMode(), model);
            return AiResponseSupport.toAiResult(chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .options(OpenAiChatOptions.builder()
                            .model(model)
                            .build())
                    .user(userMessage)
                    .call()
                    .responseEntity(ReportNarrative.class));
        } catch (Exception e) {
            log.warn("AI report narrative generation failed, using fallback: {}", e.getMessage());
            return new FallbackReportNarrativeService().generate(request);
        }
    }

    private String buildUserMessage(ReportNarrativeRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("ROLE / JOB DESCRIPTION:\n")
          .append(truncate(req.jobDescriptionText(), 1200))
          .append("\n\nSECTOR: ").append(req.sector())
          .append("\nSCORING METHOD: ").append(req.scoringMode())
          .append("\n\nSTATISTICS:")
          .append("\n  Total CVs submitted: ").append(req.totalSubmitted())
          .append("\n  Total analysed (full scoring): ").append(req.totalAnalysed())
          .append("\n  Shortlisted: ").append(req.totalShortlisted())
          .append("\n  Rejected after full scoring: ").append(req.totalRejected())
          .append("\n  Eliminated at pre-filter: ").append(req.totalEliminated());

        if (!req.shortlistedCandidates().isEmpty()) {
            sb.append("\n\nSHORTLISTED CANDIDATES:\n");
            for (CandidateEvaluation e : req.shortlistedCandidates()) {
                sb.append("  - ").append(e.candidateProfile().candidateName())
                  .append(" (score ").append(Math.round(e.score())).append("/100)")
                  .append(": ").append(e.summary()).append("\n");
            }
        }

        if (!req.rejectedCandidates().isEmpty()) {
            sb.append("\nREJECTED CANDIDATES (scored but below threshold):\n");
            for (CandidateEvaluation e : req.rejectedCandidates()) {
                sb.append("  - ").append(e.candidateProfile().candidateName())
                  .append(" (score ").append(Math.round(e.score())).append("/100)")
                  .append(": ").append(e.summary()).append("\n");
            }
        }

        if (!req.eliminatedCandidates().isEmpty()) {
            sb.append("\nPRE-FILTER ELIMINATED (").append(req.totalEliminated()).append(" candidates):\n");
            for (StoredEliminatedCandidate e : req.eliminatedCandidates()) {
                sb.append("  - ").append(e.candidateName())
                  .append(": ").append(e.eliminationReasonDisplay()).append("\n");
            }
        }

        return sb.toString();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
