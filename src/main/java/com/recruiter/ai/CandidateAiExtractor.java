package com.recruiter.ai;

public interface CandidateAiExtractor {

    AiResult<AiCandidateProfile> extract(String cvText);
}
