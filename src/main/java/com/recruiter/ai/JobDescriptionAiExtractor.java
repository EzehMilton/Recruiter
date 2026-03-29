package com.recruiter.ai;

public interface JobDescriptionAiExtractor {

    AiResult<AiJobDescriptionProfile> extract(String jobDescriptionText);
}
