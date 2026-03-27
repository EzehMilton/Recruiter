package com.recruiter.screening;

import com.recruiter.config.RecruitmentProperties;
import com.recruiter.domain.CandidateEvaluation;
import com.recruiter.domain.CandidateProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShortlistServiceTest {

    @Test
    void marksTopRequestedCandidatesAsShortlisted() {
        ShortlistService shortlistService = new ShortlistService(properties(3));

        List<CandidateEvaluation> shortlisted = shortlistService.shortlist(evaluations(), 2);

        assertThat(shortlisted).extracting(CandidateEvaluation::shortlisted)
                .containsExactly(true, true, false);
    }

    @Test
    void fallsBackToConfiguredShortlistSizeWhenRequestDoesNotProvideOne() {
        ShortlistService shortlistService = new ShortlistService(properties(1));

        List<CandidateEvaluation> shortlisted = shortlistService.shortlist(evaluations(), null);

        assertThat(shortlisted).extracting(CandidateEvaluation::shortlisted)
                .containsExactly(true, false, false);
    }

    private RecruitmentProperties properties(int shortlistCount) {
        RecruitmentProperties properties = new RecruitmentProperties();
        properties.setShortlistCount(shortlistCount);
        properties.setMaxJobDescriptionWords(1000);
        properties.setMaxCandidates(20);
        properties.setMaxFileSizeBytes(5 * 1024 * 1024);
        return properties;
    }

    private List<CandidateEvaluation> evaluations() {
        return List.of(
                evaluation("Alice", 92.0),
                evaluation("Bob", 81.0),
                evaluation("Carol", 70.0)
        );
    }

    private CandidateEvaluation evaluation(String candidateName, double score) {
        return new CandidateEvaluation(
                new CandidateProfile(candidateName, candidateName + ".pdf", "", List.of(), null),
                score,
                "summary",
                false
        );
    }
}
