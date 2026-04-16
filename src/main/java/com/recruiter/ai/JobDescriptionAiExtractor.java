package com.recruiter.ai;

import com.recruiter.domain.ScreeningPackage;

public interface JobDescriptionAiExtractor {

    default AiResult<AiJobDescriptionProfile> extract(String jobDescriptionText) {
        return extract(jobDescriptionText, ScreeningPackage.QUICK_SCREEN);
    }

    AiResult<AiJobDescriptionProfile> extract(String jobDescriptionText, ScreeningPackage screeningPackage);
}
