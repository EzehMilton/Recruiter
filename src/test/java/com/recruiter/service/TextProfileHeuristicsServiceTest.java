package com.recruiter.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextProfileHeuristicsServiceTest {

    private final TextProfileHeuristicsService heuristicsService = TextProfileHeuristicsServiceTestSupport.createService();

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

    @Test
    void shortCanonicalSkillDoesNotMatchInsideEssential() {
        List<String> skills = heuristicsService.extractSkills(
                "Essential Requirements: strong communication and teamwork"
        );

        assertThat(skills).doesNotContain("SEN");
    }

    @Test
    void shortCanonicalSkillDoesNotMatchInsidePresent() {
        List<String> skills = heuristicsService.extractSkills(
                "Staff Nurse (2022-Present) providing patient care"
        );

        assertThat(skills).doesNotContain("SEN");
    }

    @Test
    void shortCanonicalSkillStillMatchesAsStandaloneToken() {
        List<String> skills = heuristicsService.extractSkills(
                "Experience supporting SEN pupils in the classroom"
        );

        assertThat(skills).contains("SEN");
    }

    // ---- extractYearsOfExperience (contextAware=true — CV mode) ----

    @Test
    void cvPersonalMatchWinsOverCompanyHistory() {
        // "I have 3 years" is personal; "25 years of history" is non-personal
        String cv = "I have 3 years of Java experience.\nWe are a company with 25 years of history.";
        assertThat(heuristicsService.extractYearsOfExperience(cv, true)).isEqualTo(3);
    }

    @Test
    void cvYearsInPhraseClassifiedAsPersonal() {
        String cv = "10 years in the industry, working across multiple sectors.";
        assertThat(heuristicsService.extractYearsOfExperience(cv, true)).isEqualTo(10);
    }

    @Test
    void cvFallsBackToHighestOverallWhenNoPersonalIndicators() {
        // "founded" triggers no non-personal indicator ("founded in" ≠ "founded 30"), so
        // neither indicator set matches — falls back to highest overall
        String cv = "Company founded 30 years ago with strong market presence.";
        assertThat(heuristicsService.extractYearsOfExperience(cv, true)).isEqualTo(30);
    }

    @Test
    void cvExcludesMatchesAboveCap() {
        // 50 > 40, excluded by cap; no other matches → null
        String cv = "I worked for 50 years in this sector.";
        assertThat(heuristicsService.extractYearsOfExperience(cv, true)).isNull();
    }

    @Test
    void cvExcludesMatchesAboveCapAndReturnsValidPersonalMatch() {
        // 50 excluded; 8 is personal → 8
        String cv = "I have 8 years of experience.\nOur company has operated for 50 years.";
        assertThat(heuristicsService.extractYearsOfExperience(cv, true)).isEqualTo(8);
    }

    // ---- extractYearsOfExperience (contextAware=false — JD mode) ----

    @Test
    void jdReturnsHighestNumberRegardlessOfContext() {
        // JD mode ignores personal/non-personal classification; returns highest
        String jd = "Minimum 3 years required. Our firm has been established for 20 years.";
        assertThat(heuristicsService.extractYearsOfExperience(jd, false)).isEqualTo(20);
    }

    @Test
    void jdReturnsNullWhenNoYearsMentioned() {
        String jd = "Strong communication and leadership skills required.";
        assertThat(heuristicsService.extractYearsOfExperience(jd, false)).isNull();
    }

    @Test
    void fallbackUsesHardcodedSkillsWhenYamlIsMissing() {
        TextProfileHeuristicsService fallbackService =
                TextProfileHeuristicsServiceTestSupport.createService("classpath:missing-skills.yml");

        List<String> skills = fallbackService.extractSkills("Built services on Amazon Web Services with ReactJS");

        assertThat(skills).contains("AWS", "React");
    }

    @Test
    void fallbackUsesHardcodedSkillsWhenYamlIsEmpty(@TempDir Path tempDir) throws IOException {
        Path emptySkillsFile = tempDir.resolve("skills-empty.yml");
        Files.writeString(emptySkillsFile, "");

        TextProfileHeuristicsService fallbackService =
                TextProfileHeuristicsServiceTestSupport.createService(emptySkillsFile.toUri().toString());

        List<String> skills = fallbackService.extractSkills("Built services on Amazon Web Services with ReactJS");

        assertThat(skills).contains("AWS", "React");
    }

    @Test
    void skillsLoadedFromYamlAreUsedForExtraction() {
        List<String> skills = heuristicsService.extractSkills("Experienced in Java, Spring Boot and SQL.");

        assertThat(skills).contains("Java", "Spring Boot", "SQL");
    }

    @Test
    void aliasesLoadedFromYamlResolveCorrectly() {
        List<String> skills = heuristicsService.extractSkills("Designed user interfaces and built on k8s.");

        assertThat(skills).contains("UI Design", "Kubernetes");
    }

    @Test
    void addingNewSkillToYamlCausesItToBeRecognised(@TempDir Path tempDir) throws IOException {
        Path customSkillsFile = tempDir.resolve("skills.yml");
        Files.writeString(customSkillsFile, """
                skills:
                  - Java
                  - Quantum Networking
                aliases:
                  qn: quantum networking
                """);

        TextProfileHeuristicsService customService =
                TextProfileHeuristicsServiceTestSupport.createService(customSkillsFile.toUri().toString());

        assertThat(customService.extractSkills("Hands-on experience in quantum networking systems"))
                .contains("Quantum Networking");
        assertThat(customService.extractSkills("QN research background"))
                .contains("Quantum Networking");
    }

    @Test
    void classifyRequirementsSeparatesEssentialAndDesirableClausesOnSameLine() {
        RequirementClassification classification = heuristicsService.classifyRequirements(
                "Must have Java, ideally with Python",
                List.of("Java", "Python")
        );

        assertThat(classification.essentialSkills()).containsExactly("Java");
        assertThat(classification.desirableSkills()).containsExactly("Python");
        assertThat(classification.unclassifiedSkills()).isEmpty();
    }

    @Test
    void classifyRequirementsKeepsSharedEssentialContextForGroupedSkills() {
        RequirementClassification classification = heuristicsService.classifyRequirements(
                "Required: AWS and Docker",
                List.of("AWS", "Docker")
        );

        assertThat(classification.essentialSkills()).containsExactly("AWS", "Docker");
        assertThat(classification.desirableSkills()).isEmpty();
        assertThat(classification.unclassifiedSkills()).isEmpty();
    }

    @Test
    void classifyRequirementsHandlesSemicolonSeparatedClauses() {
        RequirementClassification classification = heuristicsService.classifyRequirements(
                "Python is essential; React is a nice to have",
                List.of("Python", "React")
        );

        assertThat(classification.essentialSkills()).containsExactly("Python");
        assertThat(classification.desirableSkills()).containsExactly("React");
        assertThat(classification.unclassifiedSkills()).isEmpty();
    }

    @Test
    void classifyRequirementsDoesNotLeakDesirableProductionContextOntoKubernetes() {
        RequirementClassification classification = heuristicsService.classifyRequirements(
                "Experience with Kubernetes - preferably in production",
                List.of("Kubernetes")
        );

        assertThat(classification.essentialSkills()).isEmpty();
        assertThat(classification.desirableSkills()).isEmpty();
        assertThat(classification.unclassifiedSkills()).containsExactly("Kubernetes");
    }

    @Test
    void classifyRequirementsSplitsAndIdeallyIntoSeparateClauses() {
        RequirementClassification classification = heuristicsService.classifyRequirements(
                "Must have strong communication skills and ideally project management",
                List.of("Communication", "Project Management")
        );

        assertThat(classification.essentialSkills()).containsExactly("Communication");
        assertThat(classification.desirableSkills()).containsExactly("Project Management");
        assertThat(classification.unclassifiedSkills()).isEmpty();
    }

    @Test
    void classifyRequirementsLeavesSkillsUnclassifiedWhenNoIndicatorsExist() {
        RequirementClassification classification = heuristicsService.classifyRequirements(
                "Looking for Java, Python and AWS experience in backend teams.",
                List.of("Java", "Python", "AWS")
        );

        assertThat(classification.essentialSkills()).isEmpty();
        assertThat(classification.desirableSkills()).isEmpty();
        assertThat(classification.unclassifiedSkills()).containsExactly("Java", "Python", "AWS");
    }
}
