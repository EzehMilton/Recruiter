package com.recruiter.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextProfileHeuristicsServiceTest {

    private final TextProfileHeuristicsService heuristicsService = new TextProfileHeuristicsService();

    @Test
    void extractsAdditionalDomainSkillsUsingSamePhraseMatching() {
        List<String> skills = heuristicsService.extractSkills(
                "Worked on solvency ii reporting, reserving, and capital modelling.",
                List.of("Solvency II", "Reserving", "IFRS 17")
        );

        assertThat(skills).contains("Solvency II", "Reserving");
        assertThat(skills).doesNotContain("IFRS 17");
    }
}
