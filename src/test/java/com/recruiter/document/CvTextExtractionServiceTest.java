package com.recruiter.document;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        List<ExtractedDocument> extractedDocuments = service.extractAll(files);

        assertThat(extractedDocuments).hasSize(20);
        assertThat(extractedDocuments.getFirst().originalFilename()).isEqualTo("candidate-1.pdf");
        assertThat(extractedDocuments.getLast().originalFilename()).isEqualTo("candidate-20.pdf");
    }

    @Test
    void throwsClearExceptionWhenNoExtractorSupportsTheFile() {
        CvTextExtractionService service = new CvTextExtractionService(List.of());
        MultipartFile file = new MockMultipartFile(
                "cvFiles",
                "candidate.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "docx".getBytes());

        assertThatThrownBy(() -> service.extractAll(List.of(file)))
                .isInstanceOf(DocumentExtractionException.class)
                .hasMessage("Unsupported CV file 'candidate.docx'. Only PDF files are currently supported.");
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
}
