package com.recruiter.service;

import java.util.List;

public record RequirementClassification(
        List<String> essentialSkills,
        List<String> desirableSkills,
        List<String> unclassifiedSkills
) {

    public RequirementClassification {
        essentialSkills = List.copyOf(essentialSkills);
        desirableSkills = List.copyOf(desirableSkills);
        unclassifiedSkills = List.copyOf(unclassifiedSkills);
    }
}