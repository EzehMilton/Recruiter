package com.recruiter.document;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

@Service
public class PdfDocumentExtractionService implements DocumentExtractionService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    @Override
    public boolean supports(MultipartFile file) {
        String contentType = file.getContentType();
        if (PDF_CONTENT_TYPE.equalsIgnoreCase(contentType)) {
            return true;
        }

        String filename = file.getOriginalFilename();
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    @Override
    public ExtractedDocument extract(MultipartFile file) {
        String filename = safeFilename(file);
        if (!supports(file)) {
            throw new DocumentExtractionException(
                    "Unsupported CV file '" + filename + "'. Only PDF files are currently supported.");
        }

        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            String text = new PDFTextStripper().getText(document).trim();
            return new ExtractedDocument(filename, text);
        } catch (IOException ex) {
            throw new DocumentExtractionException("Failed to extract text from PDF '" + filename + "'.", ex);
        }
    }

    private String safeFilename(MultipartFile file) {
        String filename = file.getOriginalFilename();
        return (filename == null || filename.isBlank()) ? "uploaded-file" : filename;
    }
}
