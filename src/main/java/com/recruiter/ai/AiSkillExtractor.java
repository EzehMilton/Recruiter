package com.recruiter.ai;

import com.recruiter.domain.ScreeningPackage;

public interface AiSkillExtractor {

    default AiResult<ExtractedJobSkills> extract(String jobDescriptionText) {
        return extract(jobDescriptionText, ScreeningPackage.QUICK_SCREEN);
    }

    AiResult<ExtractedJobSkills> extract(String jobDescriptionText, ScreeningPackage screeningPackage);
}
