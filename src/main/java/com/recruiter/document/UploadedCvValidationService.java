package com.recruiter.document;

import com.recruiter.config.RecruitmentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UploadedCvValidationService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String PDF_EXTENSION = ".pdf";

    private final RecruitmentProperties properties;

    public List<String> validate(List<MultipartFile> files) {
        List<String> errors = new ArrayList<>();
        if (files == null || files.isEmpty()) {
            errors.add("Please upload at least one CV.");
            return errors;
        }

        long actualCount = files.stream().filter(file -> !file.isEmpty()).count();
        int uploadCap = properties.getEffectiveUploadProcessingCap();
        if (actualCount > uploadCap) {
            errors.add("You can upload at most " + uploadCap
                    + " CVs at once (you selected " + actualCount + ").");
        }

        for (MultipartFile file : files) {
            String filename = safeFilename(file);

            if (file.isEmpty()) {
                errors.add("CV file '" + filename + "' is empty.");
                continue;
            }

            if (!hasPdfExtension(file)) {
                errors.add("CV file '" + filename + "' must use the .pdf extension.");
                continue;
            }

            if (!hasPdfContentType(file)) {
                errors.add("CV file '" + filename + "' has unsupported content type '"
                        + describeContentType(file.getContentType()) + "'. Only PDF files are accepted.");
                continue;
            }

            if (file.getSize() > properties.getMaxFileSizeBytes()) {
                errors.add("CV file '" + filename + "' exceeds the maximum size of "
                        + humanReadableSize(properties.getMaxFileSizeBytes()) + ".");
            }
        }

        return errors;
    }

    private boolean hasPdfExtension(MultipartFile file) {
        String filename = file.getOriginalFilename();
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(PDF_EXTENSION);
    }

    private boolean hasPdfContentType(MultipartFile file) {
        return PDF_CONTENT_TYPE.equalsIgnoreCase(file.getContentType());
    }

    private String describeContentType(String contentType) {
        return (contentType == null || contentType.isBlank()) ? "unknown" : contentType;
    }

    private String safeFilename(MultipartFile file) {
        String filename = file.getOriginalFilename();
        return (filename == null || filename.isBlank()) ? "uploaded-file" : filename;
    }

    private String humanReadableSize(long bytes) {
        long megabyte = 1024 * 1024;
        long kilobyte = 1024;

        if (bytes % megabyte == 0) {
            return (bytes / megabyte) + " MB";
        }
        if (bytes % kilobyte == 0) {
            return (bytes / kilobyte) + " KB";
        }
        return bytes + " bytes";
    }
}
