package com.recruiter.ai;

public interface JdQualityAssessorService {
    AiResult<JdQualityAssessment> assess(String jobDescriptionText);
}
