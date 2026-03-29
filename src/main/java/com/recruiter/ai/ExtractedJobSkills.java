package com.recruiter.ai;

import java.util.List;
import java.util.Objects;

public record ExtractedJobSkills(List<String> skills) {

    public ExtractedJobSkills {
        skills = List.copyOf(Objects.requireNonNullElse(skills, List.of()));
    }
}
