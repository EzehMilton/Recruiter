package com.recruiter.screening;

import com.recruiter.domain.CandidateProfile;
import com.recruiter.domain.JobDescriptionProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CandidateSummaryService {

    private final CandidateSummaryGenerator candidateSummaryGenerator;

    public String generate(JobDescriptionProfile jobDescriptionProfile,
                           CandidateProfile candidateProfile,
                           CandidateScoreDetails scoreDetails) {
        return candidateSummaryGenerator.generate(jobDescriptionProfile, candidateProfile, scoreDetails);
    }
}
