package com.recruiter.service;

import com.recruiter.domain.CandidateEvaluation;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class RankingService {

    public List<CandidateEvaluation> rank(List<CandidateEvaluation> evaluations) {
        return evaluations.stream()
                .sorted(Comparator.comparingDouble(CandidateEvaluation::score).reversed()
                        .thenComparing(evaluation -> evaluation.candidateProfile().candidateName()))
                .toList();
    }
}
