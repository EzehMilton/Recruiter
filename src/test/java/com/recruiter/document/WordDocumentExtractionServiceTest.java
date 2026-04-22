package com.recruiter.document;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WordDocumentExtractionServiceTest {

    private final WordDocumentExtractionService service = new WordDocumentExtractionService();

    @Test
    void extractsTextFromUploadedDocx() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "cvFiles",
                "candidate.docx",
                SupportedCvFileTypes.DOCX_CONTENT_TYPE,
                createDocx("Jane Doe Java Engineer")
        );

        ExtractedDocument extractedDocument = service.extract(file);

        assertThat(extractedDocument.originalFilename()).isEqualTo("candidate.docx");
        assertThat(extractedDocument.text()).contains("Jane Doe Java Engineer");
    }

    @Test
    void supportsLegacyDocFiles() {
        MockMultipartFile file = new MockMultipartFile(
                "cvFiles",
                "candidate.doc",
                SupportedCvFileTypes.DOC_CONTENT_TYPE,
                "legacy-doc".getBytes()
        );

        assertThat(service.supports(file)).isTrue();
    }

    @Test
    void throwsClearExceptionWhenWordExtractionFails() {
        MockMultipartFile file = new MockMultipartFile(
                "cvFiles",
                "broken.docx",
                SupportedCvFileTypes.DOCX_CONTENT_TYPE,
                "not-a-real-docx".getBytes()
        );

        assertThatThrownBy(() -> service.extract(file))
                .isInstanceOf(DocumentExtractionException.class)
                .hasMessage("Failed to extract text from Word document 'broken.docx'.");
    }

    private byte[] createDocx(String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText(text);
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
