package com.recruiter.screening;

import com.recruiter.domain.JobDescriptionProfile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicJobDescriptionProfileExtractorTest {

    private final HeuristicJobDescriptionProfileExtractor extractor =
            new HeuristicJobDescriptionProfileExtractor(new TextProfileHeuristicsService());

    @Test
    void extractsSkillsKeywordsAndExperienceFromJobDescription() {
        JobDescriptionProfile profile = extractor.extract("""
                Senior Java Engineer
                Requirements:
                - 5+ years of experience with Java and Spring Boot
                - Strong AWS and SQL skills
                - Experience with distributed systems and backend services
                """);

        assertThat(profile.requiredSkills()).contains("Java", "Spring Boot", "AWS", "SQL");
        assertThat(profile.domainKeywords()).contains("distributed", "systems", "backend", "services");
        assertThat(profile.yearsOfExperience()).isEqualTo(5);
    }
}
