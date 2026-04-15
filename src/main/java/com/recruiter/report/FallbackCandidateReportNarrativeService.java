package com.recruiter.report;

import java.util.List;

public class FallbackCandidateReportNarrativeService implements CandidateReportNarrativeService {

    @Override
    public CandidateReportNarrative generate(CandidateReportNarrativeRequest request) {
        String name = request.candidateName().isBlank() ? "This candidate" : request.candidateName();
        int score = (int) Math.round(request.score());

        String summary = name + " has been reviewed against the requirements of this role. " +
                "Based on the submitted CV, the candidate presents a background relevant to the position. " +
                "A full AI-generated profile was not available at the time of this report.";

        String fitSummary = name + " achieved a match score of " + score + " out of 100 against the role requirements. " +
                (score >= 70
                        ? "This score indicates a strong alignment with the key criteria for this position."
                        : score >= 50
                        ? "This score suggests a moderate match; some gaps were identified relative to the core requirements."
                        : "This score indicates limited alignment with the essential criteria for this role.");

        List<InterviewQuestion> questions = List.of(
                new InterviewQuestion(
                        "Can you walk me through your most relevant experience for this role?",
                        "Look for specific examples that directly address the core requirements of the job description. Strong answers will reference measurable outcomes.",
                        List.of("What was the most challenging aspect of that work?", "How did you measure success in that role?")
                ),
                new InterviewQuestion(
                        "How do you approach learning new tools or technologies required for a position?",
                        "A strong answer demonstrates adaptability and a structured approach to upskilling, ideally with a concrete recent example.",
                        List.of("Can you give an example of a skill you had to develop quickly?", "How do you stay current in your field?")
                ),
                new InterviewQuestion(
                        "Describe a situation where you had to manage competing priorities under pressure.",
                        "Look for evidence of prioritisation skills, communication with stakeholders, and a positive outcome despite constraints.",
                        List.of("What would you do differently looking back?", "How did you communicate progress to your team or manager?")
                ),
                new InterviewQuestion(
                        "What do you understand about our organisation and why does this role appeal to you?",
                        "A strong answer shows genuine research into the company and a clear, specific motivation aligned with the role and team.",
                        List.of("What aspect of the role excites you most?", "How does this role fit into your longer-term career plans?")
                ),
                new InterviewQuestion(
                        "Can you describe a professional achievement you are most proud of and why?",
                        "Look for ownership, impact, and the ability to articulate contribution clearly. Strong answers are specific and results-oriented.",
                        List.of("What obstacles did you have to overcome to achieve that?", "How did that experience shape how you work today?")
                )
        );

        return new CandidateReportNarrative(summary, List.of(), List.of(), fitSummary, questions);
    }
}
