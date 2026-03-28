package com.recruiter.orchestration.embabel;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Agent;
import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.CandidateProfile;
import com.recruiter.domain.JobDescriptionProfile;
import com.recruiter.screening.CandidateEvaluationFactory;
import com.recruiter.screening.CandidateProfileExtractor;
import com.recruiter.screening.CandidateScoringService;
import com.recruiter.screening.CandidateScoreDetails;
import com.recruiter.screening.CandidateSummaryService;
import com.recruiter.screening.JobDescriptionProfileExtractor;
import lombok.RequiredArgsConstructor;

@Agent(
        name = "candidateScreeningAgent",
        provider = "Recruiter MVP",
        description = "Coordinates job profiling and candidate evaluation using existing recruitment services."
)
@RequiredArgsConstructor
public class CandidateScreeningAgent {

    private final JobDescriptionProfileExtractor jobDescriptionProfileExtractor;
    private final CandidateProfileExtractor candidateProfileExtractor;
    private final CandidateScoringService candidateScoringService;
    private final CandidateSummaryService candidateSummaryService;
    private final CandidateEvaluationFactory candidateEvaluationFactory;

    @Action(description = "Extract a structured job description profile from submitted job text.")
    @AchievesGoal(description = "Produce a structured job description profile for downstream candidate evaluation.")
    public JobDescriptionProfile extractJobProfile(JobDescriptionText jobDescriptionText) {
        return jobDescriptionProfileExtractor.extract(jobDescriptionText.value());
    }

    @Action(description = "Extract a temporary candidate profile from one uploaded CV.")
    public CandidateProfile extractCandidateProfile(CandidateScreeningAgentRequest request) {
        return candidateProfileExtractor.extract(request.extractedDocument());
    }

    @Action(description = "Score one candidate deterministically against the structured job profile.")
    public CandidateScoreDetails scoreCandidate(CandidateScreeningAgentRequest request,
                                                CandidateProfile candidateProfile) {
        return candidateScoringService.score(request.jobDescriptionProfile(), candidateProfile);
    }

    @Action(description = "Generate a candidate-facing summary from the structured profile comparison.")
    public CandidateSummary summarizeCandidate(CandidateScreeningAgentRequest request,
                                               CandidateProfile candidateProfile,
                                               CandidateScoreDetails scoreDetails) {
        return new CandidateSummary(candidateSummaryService.generate(
                request.jobDescriptionProfile(),
                candidateProfile,
                scoreDetails
        ));
    }

    @Action(description = "Assemble the final candidate evaluation from profile, score, and summary.")
    @AchievesGoal(description = "Evaluate one candidate against the structured job profile.")
    public CandidateEvaluation buildCandidateEvaluation(CandidateProfile candidateProfile,
                                                        CandidateScoreDetails scoreDetails,
                                                        CandidateSummary candidateSummary) {
        return candidateEvaluationFactory.createSuccessful(candidateProfile, scoreDetails, candidateSummary.value());
    }
}
