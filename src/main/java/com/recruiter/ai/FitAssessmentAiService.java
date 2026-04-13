package com.recruiter.ai;

public interface FitAssessmentAiService {

    AiResult<AiFitAssessment> assess(AiJobDescriptionProfile job, AiCandidateProfile candidate);

    AiResult<AiFitAssessment> assess(AiJobDescriptionProfile job, AiCandidateProfile candidate, String systemPrompt);
}
