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

    @Test
    void aliasMatchesReactJSAndPostgreSQL() {
        List<String> skills = heuristicsService.extractSkills(
                "Experience with ReactJS and PostgreSQL"
        );
        assertThat(skills).contains("React", "PostgreSQL");
    }

    @Test
    void aliasMatchesAmazonWebServices() {
        List<String> skills = heuristicsService.extractSkills(
                "Built services on Amazon Web Services"
        );
        assertThat(skills).contains("AWS");
    }

    @Test
    void aliasAndDirectMatchDeduplicateKubernetes() {
        List<String> skills = heuristicsService.extractSkills(
                "Kubernetes (k8s) orchestration"
        );
        assertThat(skills).contains("Kubernetes");
        assertThat(skills.stream().filter("Kubernetes"::equals).count())
                .as("Kubernetes should appear exactly once")
                .isEqualTo(1);
    }

    @Test
    void shortAliasMatchesUiAndUxAsStandaloneTokens() {
        List<String> skills = heuristicsService.extractSkills(
                "UI design and UX research"
        );
        assertThat(skills).contains("UI Design", "UX Design");
    }

    @Test
    void shortAliasDoesNotMatchInsideWords() {
        List<String> skills = heuristicsService.extractSkills(
                "building a liquid user interface"
        );
        assertThat(skills).contains("UI Design");
        // "ui" inside "liquid" must NOT trigger a match — only "user interface" should
    }

    @Test
    void aliasMatchesMsOfficeAndMsExcel() {
        List<String> skills = heuristicsService.extractSkills(
                "MS Office and MS Excel proficiency"
        );
        assertThat(skills).contains("Microsoft Office", "Excel");
    }

    @Test
    void directMatchReactAndNodeJs() {
        List<String> skills = heuristicsService.extractSkills(
                "React and Node.js experience"
        );
        assertThat(skills).contains("React", "Node.js");
    }
}
