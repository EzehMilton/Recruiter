package com.recruiter.report;

import java.util.List;

public record InterviewQuestion(
        String question,
        String answerGuide,
        List<String> followUpQuestions
) {
    public InterviewQuestion {
        question = question != null ? question : "";
        answerGuide = answerGuide != null ? answerGuide : "";
        followUpQuestions = followUpQuestions != null ? List.copyOf(followUpQuestions) : List.of();
    }
}
