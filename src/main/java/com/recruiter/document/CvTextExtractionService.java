package com.recruiter.document;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

        Set<String> seenDigests = new LinkedHashSet<>();
        List<DocumentExtractionOutcome> extractionOutcomes = new ArrayList<>(nonEmptyFiles.size());

        for (MultipartFile file : nonEmptyFiles) {
            String digest = sha256(file);
            if (digest != null && !seenDigests.add(digest)) {
                log.info("Duplicate file skipped: filename='{}', sha256='{}'",
                        safeFilename(file), digest.substring(0, 12));
                continue;
            }
            extractionOutcomes.add(extract(file));
        }

        return extractionOutcomes;
    }

    private DocumentExtractionOutcome extract(MultipartFile file) {
        try {
            return DocumentExtractionOutcome.success(resolveExtractor(file).extract(file));
        } catch (DocumentExtractionException ex) {
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

    static String sha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(file.getBytes()));
        } catch (NoSuchAlgorithmException | IOException ex) {
            log.warn("Could not compute digest for '{}': {}", file.getOriginalFilename(), ex.getMessage());
            return null;
        }
    }

    private String safeFilename(MultipartFile file) {
        String filename = file.getOriginalFilename();
        return (filename == null || filename.isBlank()) ? "uploaded-file" : filename;
    }
}
