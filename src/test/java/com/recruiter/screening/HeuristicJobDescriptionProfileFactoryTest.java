package com.recruiter.screening;

import com.recruiter.domain.JobDescriptionProfile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicJobDescriptionProfileFactoryTest {

    private final HeuristicJobDescriptionProfileFactory factory =
            new HeuristicJobDescriptionProfileFactory(new TextProfileHeuristicsService());

    @Test
    void extractsSkillsKeywordsAndExperienceFromJobDescription() {
        JobDescriptionProfile profile = factory.create("""
                Senior Java Engineer
                Requirements:
                - 5+ years of experience with Java and Spring Boot
                - Strong AWS and SQL skills
                - Experience with distributed systems and backend services
                """);

        assertThat(profile.extractedSkills()).contains("Java", "Spring Boot", "AWS", "SQL");
        assertThat(profile.requiredKeywords()).contains("distributed", "systems", "backend", "services");
        assertThat(profile.yearsOfExperience()).isEqualTo(5);
    }
}
