package com.recruiter.screening;

import com.recruiter.domain.CandidateProfile;
import com.recruiter.domain.JobDescriptionProfile;

public interface CandidateSummaryGenerator {

    String generate(JobDescriptionProfile jobDescriptionProfile,
                    CandidateProfile candidateProfile,
                    CandidateScoreDetails scoreDetails);
}
