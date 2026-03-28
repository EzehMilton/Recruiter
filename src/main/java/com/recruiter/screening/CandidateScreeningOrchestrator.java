package com.recruiter.screening;

import com.recruiter.document.ExtractedDocument;
import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.JobDescriptionProfile;

public interface CandidateScreeningOrchestrator {

    JobDescriptionProfile extractJobDescriptionProfile(String jobDescriptionText);

    CandidateEvaluation evaluateCandidate(JobDescriptionProfile jobDescriptionProfile,
                                          ExtractedDocument extractedDocument);
}
