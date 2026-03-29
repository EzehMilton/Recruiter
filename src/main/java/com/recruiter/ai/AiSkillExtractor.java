package com.recruiter.ai;

public interface AiSkillExtractor {

    AiResult<ExtractedJobSkills> extract(String jobDescriptionText);
}
