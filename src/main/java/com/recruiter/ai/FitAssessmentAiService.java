package com.recruiter.ai;

public interface FitAssessmentAiService {

    AiFitAssessment assess(AiJobDescriptionProfile job, AiCandidateProfile candidate);
}
