package com.recruiter.orchestration.embabel;

import com.recruiter.document.ExtractedDocument;
import com.recruiter.domain.JobDescriptionProfile;

import java.util.Objects;

public record CandidateScreeningAgentRequest(
        JobDescriptionProfile jobDescriptionProfile,
        ExtractedDocument extractedDocument
) {

    public CandidateScreeningAgentRequest {
        jobDescriptionProfile = Objects.requireNonNull(jobDescriptionProfile, "jobDescriptionProfile must not be null");
        extractedDocument = Objects.requireNonNull(extractedDocument, "extractedDocument must not be null");
    }
}
