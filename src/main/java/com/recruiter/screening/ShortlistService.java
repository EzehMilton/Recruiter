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

    public List<CandidateEvaluation> shortlist(List<CandidateEvaluation> rankedEvaluations,
                                               Integer requestedShortlistCount,
                                               Double requestedMinimumScore) {
        return shortlist(rankedEvaluations,
                resolveShortlistCount(requestedShortlistCount),
                resolveMinimumScore(requestedMinimumScore));
    }

    public List<CandidateEvaluation> shortlist(List<CandidateEvaluation> rankedEvaluations,
                                               int shortlistCount,
                                               double minimumScore) {
        return java.util.stream.IntStream.range(0, rankedEvaluations.size())
                .mapToObj(index -> {
                    CandidateEvaluation evaluation = rankedEvaluations.get(index);
                    boolean shortlisted = index < shortlistCount && evaluation.score() >= minimumScore;
                    return new CandidateEvaluation(
                            evaluation.candidateProfile(),
                            evaluation.score(),
                            evaluation.scoreBreakdown(),
                            evaluation.scoringPath(),
                            evaluation.summary(),
                            shortlisted,
                            evaluation.aiConfidence(),
                            evaluation.aiTopStrengths(),
                            evaluation.aiTopGaps(),
                            evaluation.aiInterviewProbeAreas()
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

    public double resolveMinimumScore(Double requestedMinimumScore) {
        if (requestedMinimumScore == null || requestedMinimumScore < 0) {
            return properties.getMinimumShortlistScore();
        }
        return requestedMinimumScore;
    }
}
