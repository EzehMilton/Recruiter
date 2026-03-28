package com.recruiter.orchestration.embabel;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.recruiter.document.ExtractedDocument;
import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.JobDescriptionProfile;
import com.recruiter.screening.CandidateScreeningOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
@RequiredArgsConstructor
public class EmbabelCandidateScreeningOrchestrator implements CandidateScreeningOrchestrator {

    private final AgentPlatform agentPlatform;

    @Override
    public JobDescriptionProfile extractJobDescriptionProfile(String jobDescriptionText) {
        return AgentInvocation.create(agentPlatform, JobDescriptionProfile.class)
                .invoke(new JobDescriptionText(jobDescriptionText));
    }

    @Override
    public CandidateEvaluation evaluateCandidate(JobDescriptionProfile jobDescriptionProfile,
                                                 ExtractedDocument extractedDocument) {
        return AgentInvocation.create(agentPlatform, CandidateEvaluation.class)
                .invoke(new CandidateScreeningAgentRequest(jobDescriptionProfile, extractedDocument));
    }
}
