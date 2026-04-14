package com.recruiter.service;

import com.recruiter.document.DocumentExtractionOutcome;
import com.recruiter.document.ExtractedDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CvDeduplicationServiceTest {

    private final TextProfileHeuristicsService heuristicsService = TextProfileHeuristicsServiceTestSupport.createService();
    private final HeuristicCandidateProfileFactory candidateProfileFactory =
            new HeuristicCandidateProfileFactory(heuristicsService);
    private final CvDeduplicationService deduplicationService = new CvDeduplicationService(candidateProfileFactory);

    @Test
    void removesIdenticalTextAsExactDuplicate() {
        CvDeduplicationService.DeduplicationResult result = deduplicationService.deduplicate(List.of(
                success("alice-1.pdf", "Alice Smith\nJava Spring AWS\n6 years experience"),
                success("alice-2.pdf", "Alice Smith\nJava Spring AWS\n6 years experience")
        ));

        assertThat(result.exactDuplicatesRemoved()).isEqualTo(1);
        assertThat(result.nearDuplicatesRemoved()).isZero();
        assertThat(result.outcomes()).hasSize(1);
        assertThat(result.outcomes().getFirst().originalFilename()).isEqualTo("alice-1.pdf");
    }

    @Test
    void removesWhitespaceAndCaseVariantsAsExactDuplicate() {
        CvDeduplicationService.DeduplicationResult result = deduplicationService.deduplicate(List.of(
                success("alice-1.pdf", "Alice Smith\nJava Spring AWS\n6 years experience"),
                success("alice-2.pdf", "  ALICE SMITH  \n\nJAVA   SPRING AWS\n6 YEARS EXPERIENCE  ")
        ));

        assertThat(result.exactDuplicatesRemoved()).isEqualTo(1);
        assertThat(result.nearDuplicatesRemoved()).isZero();
        assertThat(result.outcomes()).hasSize(1);
    }

    @Test
    void removesNearDuplicateWithSameCandidateNameAndHighWordOverlap() {
        CvDeduplicationService.DeduplicationResult result = deduplicationService.deduplicate(List.of(
                success("alice-short.pdf", """
                        Alice Smith
                        Java Spring AWS SQL Docker Kubernetes microservices backend platform delivery leadership mentoring architecture.
                        """),
                success("alice-long.pdf", """
                        Alice Smith
                        Java Spring AWS SQL Docker Kubernetes microservices backend platform delivery leadership mentoring architecture optimisation.
                        """)
        ));

        assertThat(result.exactDuplicatesRemoved()).isZero();
        assertThat(result.nearDuplicatesRemoved()).isEqualTo(1);
        assertThat(result.outcomes()).hasSize(1);
        assertThat(result.outcomes().getFirst().originalFilename()).isEqualTo("alice-long.pdf");
    }

    @Test
    void keepsFilesWithSameCandidateNameButLowSimilarity() {
        CvDeduplicationService.DeduplicationResult result = deduplicationService.deduplicate(List.of(
                success("alice-tech.pdf", """
                        Alice Smith
                        Java Spring AWS SQL Docker Kubernetes microservices backend APIs distributed systems.
                        """),
                success("alice-creative.pdf", """
                        Alice Smith
                        Graphic design branding illustration photography copywriting video editing campaign planning.
                        """)
        ));

        assertThat(result.exactDuplicatesRemoved()).isZero();
        assertThat(result.nearDuplicatesRemoved()).isZero();
        assertThat(result.outcomes()).hasSize(2);
    }

    @Test
    void keepsFilesWithDifferentCandidateNamesEvenWhenContentIsSimilar() {
        CvDeduplicationService.DeduplicationResult result = deduplicationService.deduplicate(List.of(
                success("alice.pdf", """
                        Alice Smith
                        Java Spring AWS SQL Docker Kubernetes microservices backend APIs.
                        """),
                success("bob.pdf", """
                        Bob Jones
                        Java Spring AWS SQL Docker Kubernetes microservices backend APIs.
                        """)
        ));

        assertThat(result.exactDuplicatesRemoved()).isZero();
        assertThat(result.nearDuplicatesRemoved()).isZero();
        assertThat(result.outcomes()).hasSize(2);
    }

    @Test
    void keepsSingleFileWithoutDuplicates() {
        CvDeduplicationService.DeduplicationResult result = deduplicationService.deduplicate(List.of(
                success("alice.pdf", "Alice Smith\nJava Spring AWS")
        ));

        assertThat(result.exactDuplicatesRemoved()).isZero();
        assertThat(result.nearDuplicatesRemoved()).isZero();
        assertThat(result.outcomes()).hasSize(1);
    }

    private DocumentExtractionOutcome success(String filename, String text) {
        return DocumentExtractionOutcome.success(new ExtractedDocument(filename, text));
    }
}
