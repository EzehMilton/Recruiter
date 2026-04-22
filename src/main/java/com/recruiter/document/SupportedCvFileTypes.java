package com.recruiter.document;

import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

final class SupportedCvFileTypes {

    static final String PDF_CONTENT_TYPE = "application/pdf";
    static final String DOC_CONTENT_TYPE = "application/msword";
    static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    static final String OCTET_STREAM_CONTENT_TYPE = "application/octet-stream";

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".pdf", ".doc", ".docx");
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            PDF_CONTENT_TYPE,
            DOC_CONTENT_TYPE,
            DOCX_CONTENT_TYPE
    );

    private SupportedCvFileTypes() {
    }

    static boolean hasSupportedExtension(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return false;
        }
        String normalized = filename.toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.stream().anyMatch(normalized::endsWith);
    }

    static boolean hasSupportedContentType(MultipartFile file) {
        String contentType = normalizeContentType(file.getContentType());
        return contentType == null
                || OCTET_STREAM_CONTENT_TYPE.equals(contentType)
                || SUPPORTED_CONTENT_TYPES.contains(contentType);
    }

    static boolean isPdf(MultipartFile file) {
        return hasExtension(file, ".pdf") || PDF_CONTENT_TYPE.equals(normalizeContentType(file.getContentType()));
    }

    static boolean isDoc(MultipartFile file) {
        return hasExtension(file, ".doc") || DOC_CONTENT_TYPE.equals(normalizeContentType(file.getContentType()));
    }

    static boolean isDocx(MultipartFile file) {
        return hasExtension(file, ".docx") || DOCX_CONTENT_TYPE.equals(normalizeContentType(file.getContentType()));
    }

    static String acceptedFormatsDescription() {
        return "PDF, DOC, or DOCX";
    }

    static String acceptedExtensionsDescription() {
        return ".pdf, .doc, or .docx";
    }

    private static boolean hasExtension(MultipartFile file, String extension) {
        String filename = file.getOriginalFilename();
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(extension);
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        return contentType.toLowerCase(Locale.ROOT);
    }
}
