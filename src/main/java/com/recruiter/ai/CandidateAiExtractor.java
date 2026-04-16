package com.recruiter.ai;

import com.recruiter.domain.ScreeningPackage;

public interface CandidateAiExtractor {

    default AiResult<AiCandidateProfile> extract(String cvText) {
        return extract(cvText, ScreeningPackage.QUICK_SCREEN);
    }

    AiResult<AiCandidateProfile> extract(String cvText, ScreeningPackage screeningPackage);
}
