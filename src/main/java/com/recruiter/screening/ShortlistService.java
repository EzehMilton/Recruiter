package com.recruiter.screening;

import com.recruiter.config.RecruitmentProperties;
import com.recruiter.domain.CandidateEvaluation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShortlistService {

    private final RecruitmentProperties properties;

    public List<CandidateEvaluation> shortlist(List<CandidateEvaluation> rankedEvaluations, Integer requestedShortlistCount) {
        return shortlist(rankedEvaluations, resolveShortlistCount(requestedShortlistCount));
    }

    public List<CandidateEvaluation> shortlist(List<CandidateEvaluation> rankedEvaluations, int shortlistCount) {
        return java.util.stream.IntStream.range(0, rankedEvaluations.size())
                .mapToObj(index -> {
                    CandidateEvaluation evaluation = rankedEvaluations.get(index);
                    boolean shortlisted = index < shortlistCount && evaluation.score() > 0.0;
                    return new CandidateEvaluation(
                            evaluation.candidateProfile(),
                            evaluation.score(),
                            evaluation.summary(),
                            shortlisted
                    );
                })
                .toList();
    }

    public int resolveShortlistCount(Integer requestedShortlistCount) {
        if (requestedShortlistCount == null || requestedShortlistCount < 1) {
            return properties.getShortlistCount();
        }
        return requestedShortlistCount;
    }
}
