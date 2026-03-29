package com.recruiter.document;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CvTextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(CvTextExtractionService.class);

    private final List<DocumentExtractionService> extractionServices;

    public List<DocumentExtractionOutcome> extractAll(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        List<MultipartFile> nonEmptyFiles = files.stream()
                .filter(file -> !file.isEmpty())
                .toList();

        long startedAt = System.currentTimeMillis();
        List<DocumentExtractionOutcome> extractionOutcomes = new ArrayList<>(nonEmptyFiles.size());
        for (MultipartFile file : nonEmptyFiles) {
            extractionOutcomes.add(extract(file));
        }
        long totalDurationMs = System.currentTimeMillis() - startedAt;
        long successCount = extractionOutcomes.stream().filter(DocumentExtractionOutcome::succeeded).count();
        long failureCount = extractionOutcomes.size() - successCount;
        log.info("CV text extraction complete: {} files in {} ({} succeeded, {} failed)",
                extractionOutcomes.size(),
                formatElapsed(totalDurationMs),
                successCount,
                failureCount);
        return extractionOutcomes;
    }

    private DocumentExtractionOutcome extract(MultipartFile file) {
        long startedAt = System.currentTimeMillis();
        try {
            DocumentExtractionOutcome outcome = DocumentExtractionOutcome.success(resolveExtractor(file).extract(file));
            log.info("Extracted text from '{}' ({}) in {}",
                    safeFilename(file),
                    formatFileSize(file.getSize()),
                    formatElapsed(System.currentTimeMillis() - startedAt));
            return outcome;
        } catch (DocumentExtractionException ex) {
            long durationMs = System.currentTimeMillis() - startedAt;
            log.warn("Failed to extract '{}' in {}: {}: {}",
                    safeFilename(file),
                    formatElapsed(durationMs),
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
            return DocumentExtractionOutcome.failure(safeFilename(file), ex.getMessage());
        }
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

    private String formatFileSize(long sizeBytes) {
        return String.format(java.util.Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0));
    }

    private String formatElapsed(long durationMs) {
        if (durationMs < 1000) {
            return durationMs + "ms";
        }
        return String.format(java.util.Locale.US, "%.1fs", durationMs / 1000.0);
    }
}
