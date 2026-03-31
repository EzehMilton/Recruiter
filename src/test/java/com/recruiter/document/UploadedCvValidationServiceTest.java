package com.recruiter.document;

import com.recruiter.config.RecruitmentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class UploadedCvValidationServiceTest {

    @Test
    void acceptsValidPdfUploads() {
        UploadedCvValidationService service = new UploadedCvValidationService(properties());
        List<MultipartFile> files = List.of(
                new MockMultipartFile("cvFiles", "candidate.pdf", "application/pdf", "pdf-content".getBytes())
        );

        List<String> errors = service.validate(files);

        assertThat(errors).isEmpty();
    }

    @Test
    void rejectsEmptyUploadedFiles() {
        UploadedCvValidationService service = new UploadedCvValidationService(properties());
        List<MultipartFile> files = List.of(
                new MockMultipartFile("cvFiles", "empty.pdf", "application/pdf", new byte[0])
        );

        List<String> errors = service.validate(files);

        assertThat(errors).containsExactly("CV file 'empty.pdf' is empty.");
    }

    @Test
    void rejectsFilesWithUnsupportedExtension() {
        UploadedCvValidationService service = new UploadedCvValidationService(properties());
        List<MultipartFile> files = List.of(
                new MockMultipartFile("cvFiles", "candidate.txt", "application/pdf", "content".getBytes())
        );

        List<String> errors = service.validate(files);

        assertThat(errors).containsExactly("CV file 'candidate.txt' must use the .pdf extension.");
    }

    @Test
    void rejectsFilesWithUnsupportedContentType() {
        UploadedCvValidationService service = new UploadedCvValidationService(properties());
        List<MultipartFile> files = List.of(
                new MockMultipartFile("cvFiles", "candidate.pdf", "text/plain", "content".getBytes())
        );

        List<String> errors = service.validate(files);

        assertThat(errors).containsExactly(
                "CV file 'candidate.pdf' has unsupported content type 'text/plain'. Only PDF files are accepted.");
    }

    @Test
    void rejectsFilesLargerThanConfiguredMaximum() {
        UploadedCvValidationService service = new UploadedCvValidationService(properties());
        byte[] content = new byte[(int) (5 * 1024 * 1024) + 1];
        List<MultipartFile> files = List.of(
                new MockMultipartFile("cvFiles", "candidate.pdf", "application/pdf", content)
        );

        List<String> errors = service.validate(files);

        assertThat(errors).containsExactly("CV file 'candidate.pdf' exceeds the maximum size of 1 MB.");
    }

    @Test
    void rejectsMoreThanUploadProcessingCap() {
        RecruitmentProperties props = properties();
        props.setUploadProcessingCap(20);
        UploadedCvValidationService service = new UploadedCvValidationService(props);
        List<MultipartFile> files = IntStream.rangeClosed(1, 21)
                .mapToObj(index -> new MockMultipartFile(
                        "cvFiles",
                        "candidate-" + index + ".pdf",
                        "application/pdf",
                        ("pdf-" + index).getBytes()))
                .map(MultipartFile.class::cast)
                .toList();

        List<String> errors = service.validate(files);

        assertThat(errors).contains("You can upload at most 20 CVs at once (you selected 21).");
    }

    private RecruitmentProperties properties() {
        RecruitmentProperties properties = new RecruitmentProperties();
        properties.setShortlistCount(3);
        properties.setMaxJobDescriptionWords(1000);
        properties.setAnalysisCap(20);
        properties.setMaxFileSizeBytes(5 * 1024 * 1024);
        return properties;
    }
}
