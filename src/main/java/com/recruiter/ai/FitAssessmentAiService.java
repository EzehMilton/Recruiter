package com.recruiter.ai;

import com.recruiter.domain.ScreeningPackage;

public interface FitAssessmentAiService {

    default AiResult<AiFitAssessment> assess(AiJobDescriptionProfile job, AiCandidateProfile candidate) {
        return assess(job, candidate, ScreeningPackage.QUICK_SCREEN);
    }

    default AiResult<AiFitAssessment> assess(AiJobDescriptionProfile job, AiCandidateProfile candidate,
                                             String systemPrompt) {
        return assess(job, candidate, systemPrompt, ScreeningPackage.QUICK_SCREEN);
    }

    AiResult<AiFitAssessment> assess(AiJobDescriptionProfile job, AiCandidateProfile candidate,
                                     ScreeningPackage screeningPackage);

    AiResult<AiFitAssessment> assess(AiJobDescriptionProfile job, AiCandidateProfile candidate,
                                     String systemPrompt, ScreeningPackage screeningPackage);
}
