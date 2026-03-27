package com.recruiter.document;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfDocumentExtractionServiceTest {

    private final PdfDocumentExtractionService service = new PdfDocumentExtractionService();

    @Test
    void extractsTextFromUploadedPdf() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "cvFiles",
                "candidate.pdf",
                "application/pdf",
                createPdf("Jane Doe Java Engineer")
        );

        ExtractedDocument extractedDocument = service.extract(file);

        assertThat(extractedDocument.originalFilename()).isEqualTo("candidate.pdf");
        assertThat(extractedDocument.text()).contains("Jane Doe Java Engineer");
    }

    @Test
    void throwsClearExceptionWhenPdfExtractionFails() {
        MockMultipartFile file = new MockMultipartFile(
                "cvFiles",
                "broken.pdf",
                "application/pdf",
                "not-a-real-pdf".getBytes()
        );

        assertThatThrownBy(() -> service.extract(file))
                .isInstanceOf(DocumentExtractionException.class)
                .hasMessage("Failed to extract text from PDF 'broken.pdf'.");
    }

    private byte[] createPdf(String text) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(72, 720);
                contentStream.showText(text);
                contentStream.endText();
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }
}
