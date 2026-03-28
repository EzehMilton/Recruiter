package com.recruiter.screening;

import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.CandidateProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RankingServiceTest {

    private final RankingService rankingService = new RankingService();

    @Test
    void ranksCandidatesByDescendingScore() {
        CandidateEvaluation lower = new CandidateEvaluation(candidate("Beta Candidate"), 55.0, null, "lower", false);
        CandidateEvaluation higher = new CandidateEvaluation(candidate("Alpha Candidate"), 82.0, null, "higher", false);

        List<CandidateEvaluation> ranked = rankingService.rank(List.of(lower, higher));

        assertThat(ranked).extracting(CandidateEvaluation::score).containsExactly(82.0, 55.0);
    }

    @Test
    void usesCandidateNameAsTieBreaker() {
        CandidateEvaluation beta = new CandidateEvaluation(candidate("Beta Candidate"), 70.0, null, "beta", false);
        CandidateEvaluation alpha = new CandidateEvaluation(candidate("Alpha Candidate"), 70.0, null, "alpha", false);

        List<CandidateEvaluation> ranked = rankingService.rank(List.of(beta, alpha));

        assertThat(ranked).extracting(evaluation -> evaluation.candidateProfile().candidateName())
                .containsExactly("Alpha Candidate", "Beta Candidate");
    }

    private CandidateProfile candidate(String candidateName) {
        return new CandidateProfile(candidateName, candidateName + ".pdf", "", List.of(), List.of(), List.of(), null);
    }
}
