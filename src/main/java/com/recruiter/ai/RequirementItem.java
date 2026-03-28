package com.recruiter.ai;

public record RequirementItem(
        String requirement,
        RequirementType type,
        ImportanceLevel importance,
        String evidenceHint
) {
}
