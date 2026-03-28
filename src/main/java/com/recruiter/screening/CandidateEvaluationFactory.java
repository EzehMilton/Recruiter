package com.recruiter.screening;

import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.CandidateProfile;
import org.springframework.stereotype.Service;

@Service
public class CandidateEvaluationFactory {

    public CandidateEvaluation createSuccessful(CandidateProfile candidateProfile,
                                                CandidateScoreDetails scoreDetails,
                                                String summary) {
        return new CandidateEvaluation(candidateProfile, scoreDetails.totalScore(), scoreDetails, summary, false);
    }

    public CandidateEvaluation createFailed(CandidateProfile candidateProfile, String summary) {
        return new CandidateEvaluation(candidateProfile, 0.0, null, summary, false);
    }
}
