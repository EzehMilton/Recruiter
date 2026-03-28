package com.recruiter.screening;

import com.recruiter.document.ExtractedDocument;
import com.recruiter.domain.CandidateProfile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicCandidateProfileExtractorTest {

    private final HeuristicCandidateProfileExtractor extractor =
            new HeuristicCandidateProfileExtractor(new TextProfileHeuristicsService());

    @Test
    void extractsCandidateNameFromOpeningLines() {
        CandidateProfile profile = extractor.extract(new ExtractedDocument(
                "candidate.pdf",
                """
                Jane Doe
                Senior Java Engineer
                jane@example.com
                Java Spring Boot SQL AWS
                6 years experience
                """
        ));

        assertThat(profile.candidateName()).isEqualTo("Jane Doe");
        assertThat(profile.skills()).contains("Java", "Spring Boot", "SQL", "AWS");
        assertThat(profile.yearsOfExperience()).isEqualTo(6);
    }

    @Test
    void fallsBackToFilenameWhenOpeningLinesDoNotLookLikeAName() {
        CandidateProfile profile = extractor.extract(new ExtractedDocument(
                "john-smith-resume.pdf",
                """
                PROFESSIONAL SUMMARY
                Senior backend engineer focused on distributed systems
                Experience
                Java Spring AWS
                """
        ));

        assertThat(profile.candidateName()).isEqualTo("John Smith");
    }

    @Test
    void keepsUnknownCandidateFallbackForUnusableFilename() {
        CandidateProfile profile = extractor.extract(new ExtractedDocument(
                "___ .pdf",
                "Skills Java Spring Boot"
        ));

        assertThat(profile.candidateName()).isEqualTo("Unknown Candidate");
    }
}
