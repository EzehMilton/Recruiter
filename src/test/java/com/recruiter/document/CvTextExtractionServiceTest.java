package com.recruiter.document;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CvTextExtractionServiceTest {

    @Test
    void extractsTextFromTwentyUploadedCvs() {
        CvTextExtractionService service = new CvTextExtractionService(List.of(new StubDocumentExtractionService()));
        List<MultipartFile> files = java.util.stream.IntStream.rangeClosed(1, 20)
                .mapToObj(index -> new MockMultipartFile(
                        "cvFiles",
                        "candidate-" + index + ".pdf",
                        "application/pdf",
                        ("pdf-" + index).getBytes()))
                .map(MultipartFile.class::cast)
                .toList();

        List<DocumentExtractionOutcome> extractionOutcomes = service.extractAll(files);

        assertThat(extractionOutcomes).hasSize(20);
        assertThat(extractionOutcomes).allMatch(DocumentExtractionOutcome::succeeded);
        assertThat(extractionOutcomes.getFirst().originalFilename()).isEqualTo("candidate-1.pdf");
        assertThat(extractionOutcomes.getLast().originalFilename()).isEqualTo("candidate-20.pdf");
    }

    @Test
    void recordsFailureWhenNoExtractorSupportsTheFile() {
        CvTextExtractionService service = new CvTextExtractionService(List.of());
        MultipartFile file = new MockMultipartFile(
                "cvFiles",
                "candidate.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "docx".getBytes());

        List<DocumentExtractionOutcome> extractionOutcomes = service.extractAll(List.of(file));

        assertThat(extractionOutcomes).hasSize(1);
        assertThat(extractionOutcomes.getFirst().succeeded()).isFalse();
        assertThat(extractionOutcomes.getFirst().failureMessage())
                .isEqualTo("Unsupported CV file 'candidate.docx'. Only PDF files are currently supported.");
    }

    @Test
    void recordsFailureAndContinuesWhenOneFileCannotBeExtracted() {
        CvTextExtractionService service = new CvTextExtractionService(List.of(new SelectiveDocumentExtractionService()));
        List<MultipartFile> files = List.of(
                new MockMultipartFile("cvFiles", "good.pdf", "application/pdf", "good".getBytes()),
                new MockMultipartFile("cvFiles", "broken.pdf", "application/pdf", "bad".getBytes())
        );

        List<DocumentExtractionOutcome> extractionOutcomes = service.extractAll(files);

        assertThat(extractionOutcomes).hasSize(2);
        assertThat(extractionOutcomes.get(0).succeeded()).isTrue();
        assertThat(extractionOutcomes.get(1).succeeded()).isFalse();
        assertThat(extractionOutcomes.get(1).failureMessage())
                .isEqualTo("Failed to extract text from PDF 'broken.pdf'.");
    }

    private static final class StubDocumentExtractionService implements DocumentExtractionService {

        @Override
        public boolean supports(MultipartFile file) {
            return true;
        }

        @Override
        public ExtractedDocument extract(MultipartFile file) {
            return new ExtractedDocument(file.getOriginalFilename(), "stub-text");
        }
    }

    @Test
    void skipsDuplicateFilesWithIdenticalContent() {
        CvTextExtractionService service = new CvTextExtractionService(List.of(new StubDocumentExtractionService()));
        byte[] sameContent = "identical pdf content".getBytes();

        List<MultipartFile> files = List.of(
                new MockMultipartFile("cvFiles", "alice.pdf", "application/pdf", sameContent),
                new MockMultipartFile("cvFiles", "alice-copy.pdf", "application/pdf", sameContent),
                new MockMultipartFile("cvFiles", "bob.pdf", "application/pdf", "different content".getBytes())
        );

        List<DocumentExtractionOutcome> outcomes = service.extractAll(files);

        assertThat(outcomes).hasSize(2);
        assertThat(outcomes.get(0).originalFilename()).isEqualTo("alice.pdf");
        assertThat(outcomes.get(1).originalFilename()).isEqualTo("bob.pdf");
    }

    @Test
    void allowsFilesWithDifferentContentButSameName() {
        CvTextExtractionService service = new CvTextExtractionService(List.of(new StubDocumentExtractionService()));

        List<MultipartFile> files = List.of(
                new MockMultipartFile("cvFiles", "resume.pdf", "application/pdf", "version 1".getBytes()),
                new MockMultipartFile("cvFiles", "resume.pdf", "application/pdf", "version 2".getBytes())
        );

        List<DocumentExtractionOutcome> outcomes = service.extractAll(files);

        assertThat(outcomes).hasSize(2);
    }

    private static final class SelectiveDocumentExtractionService implements DocumentExtractionService {

        @Override
        public boolean supports(MultipartFile file) {
            return true;
        }

        @Override
        public ExtractedDocument extract(MultipartFile file) {
            if ("broken.pdf".equals(file.getOriginalFilename())) {
                throw new DocumentExtractionException("Failed to extract text from PDF 'broken.pdf'.");
            }
            return new ExtractedDocument(file.getOriginalFilename(), "stub-text");
        }
    }
}
