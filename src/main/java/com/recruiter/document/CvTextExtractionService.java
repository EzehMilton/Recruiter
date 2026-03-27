package com.recruiter.document;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CvTextExtractionService {

    private final List<DocumentExtractionService> extractionServices;

    public List<ExtractedDocument> extractAll(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        List<MultipartFile> nonEmptyFiles = files.stream()
                .filter(file -> !file.isEmpty())
                .toList();

        List<ExtractedDocument> extractedDocuments = new ArrayList<>(nonEmptyFiles.size());
        for (MultipartFile file : nonEmptyFiles) {
            extractedDocuments.add(resolveExtractor(file).extract(file));
        }
        return extractedDocuments;
    }

    private DocumentExtractionService resolveExtractor(MultipartFile file) {
        return extractionServices.stream()
                .filter(service -> service.supports(file))
                .findFirst()
                .orElseThrow(() -> new DocumentExtractionException(
                        "Unsupported CV file '" + safeFilename(file) + "'. Only PDF files are currently supported."));
    }

    private String safeFilename(MultipartFile file) {
        String filename = file.getOriginalFilename();
        return (filename == null || filename.isBlank()) ? "uploaded-file" : filename;
    }
}
