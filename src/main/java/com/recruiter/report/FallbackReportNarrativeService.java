package com.recruiter.report;

import com.recruiter.ai.AiResult;

/**
 * Used when AI services are not configured. Generates factual, template-based
 * narrative from the batch statistics alone.
 */
public class FallbackReportNarrativeService implements ReportNarrativeService {

    @Override
    public AiResult<ReportNarrative> generate(ReportNarrativeRequest req) {
        return new AiResult<>(new ReportNarrative(
                buildExecutiveSummary(req),
                buildMethodology(req),
                buildNextSteps(req)
        ), null);
    }

    private String buildExecutiveSummary(ReportNarrativeRequest req) {
        String sector = req.sector() != null && !req.sector().isBlank() ? req.sector() : "the advertised role";
        if (req.totalShortlisted() == 0) {
            return String.format(
                    "A candidate screening was conducted for %s. A total of %d CV(s) were submitted, of which %d were " +
                    "fully assessed against the role requirements. Following the evaluation, no candidates met the " +
                    "minimum threshold for shortlisting. The findings of this screening are set out in the report below.",
                    sector, req.totalSubmitted(), req.totalAnalysed());
        }
        return String.format(
                "A candidate screening was conducted for %s. A total of %d CV(s) were submitted, of which %d were " +
                "fully assessed against the role requirements. Following the evaluation, %d candidate(s) met the " +
                "required standard and have been shortlisted for the next stage. %d candidate(s) did not meet the " +
                "minimum threshold and have been rejected at this stage.",
                sector, req.totalSubmitted(), req.totalAnalysed(),
                req.totalShortlisted(), req.totalRejected());
    }

    private String buildMethodology(ReportNarrativeRequest req) {
        boolean isAi = "ai".equalsIgnoreCase(req.scoringMode());
        String scoringDesc = isAi
                ? "Candidates were assessed using an AI-driven multi-dimensional fit evaluation, weighing essential " +
                  "requirements, relevant experience, domain knowledge, desirable attributes, and credentials against " +
                  "the job description."
                : "Candidates were assessed using a weighted heuristic scoring model evaluating skills alignment, " +
                  "keyword relevance, and years of experience against the job description requirements.";
        String filterDesc = req.totalEliminated() > 0
                ? " A preliminary relevance filter was applied prior to full scoring, removing " +
                  req.totalEliminated() + " submission(s) that did not meet the minimum relevance threshold."
                : "";
        return scoringDesc + filterDesc +
               " Candidates scoring above the shortlist threshold were recommended for progression.";
    }

    private String buildNextSteps(ReportNarrativeRequest req) {
        if (req.totalShortlisted() == 0) {
            return """
                    • Review the role requirements and consider whether the job description accurately reflects \
                    the market available talent pool.
                    • Consider broadening the search criteria or adjusting the required experience thresholds.
                    • Re-advertise the position on additional channels to attract a wider applicant base.
                    • Review rejected candidates for any near-miss profiles that may be suitable with additional \
                    support or training.""";
        }
        return """
                • Schedule interviews with all shortlisted candidates at the earliest opportunity.
                • Prepare a structured interview guide tailored to the specific requirements of the role.
                • Request and verify professional references for preferred candidates prior to offer.
                • Provide timely feedback to unsuccessful applicants in line with your candidate experience policy.
                • Keep shortlisted candidates warm with regular communication to avoid drop-off.""";
    }
}
