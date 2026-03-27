package com.recruiter.document;

public record DocumentExtractionOutcome(
        String originalFilename,
        ExtractedDocument extractedDocument,
        String failureMessage
) {

    public DocumentExtractionOutcome {
        originalFilename = (originalFilename == null || originalFilename.isBlank()) ? "uploaded-file" : originalFilename;
    }

    public static DocumentExtractionOutcome success(ExtractedDocument extractedDocument) {
        return new DocumentExtractionOutcome(extractedDocument.originalFilename(), extractedDocument, null);
    }

    public static DocumentExtractionOutcome failure(String originalFilename, String failureMessage) {
        return new DocumentExtractionOutcome(originalFilename, null, failureMessage);
    }

    public boolean succeeded() {
        return extractedDocument != null;
    }
}
