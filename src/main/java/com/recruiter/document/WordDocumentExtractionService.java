package com.recruiter.document;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Service
public class WordDocumentExtractionService implements DocumentExtractionService {

    @Override
    public boolean supports(MultipartFile file) {
        return SupportedCvFileTypes.isDoc(file) || SupportedCvFileTypes.isDocx(file);
    }

    @Override
    public ExtractedDocument extract(MultipartFile file) {
        String filename = safeFilename(file);
        if (SupportedCvFileTypes.isDocx(file)) {
            return extractDocx(file, filename);
        }
        if (SupportedCvFileTypes.isDoc(file)) {
            return extractDoc(file, filename);
        }
        throw new DocumentExtractionException(
                "Unsupported CV file '" + filename + "'. Only "
                        + SupportedCvFileTypes.acceptedFormatsDescription() + " files are currently supported.");
    }

    private ExtractedDocument extractDocx(MultipartFile file, String filename) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(file.getBytes()));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return new ExtractedDocument(filename, extractor.getText().trim());
        } catch (Exception ex) {
            throw new DocumentExtractionException("Failed to extract text from Word document '" + filename + "'.", ex);
        }
    }

    private ExtractedDocument extractDoc(MultipartFile file, String filename) {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(file.getBytes()));
             WordExtractor extractor = new WordExtractor(document)) {
            return new ExtractedDocument(filename, extractor.getText().trim());
        } catch (Exception ex) {
            throw new DocumentExtractionException("Failed to extract text from Word document '" + filename + "'.", ex);
        }
    }

    private String safeFilename(MultipartFile file) {
        String filename = file.getOriginalFilename();
        return (filename == null || filename.isBlank()) ? "uploaded-file" : filename;
    }
}
