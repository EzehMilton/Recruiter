package com.recruiter.document;

import org.springframework.web.multipart.MultipartFile;

public interface DocumentExtractionService {

    boolean supports(MultipartFile file);

    ExtractedDocument extract(MultipartFile file);
}
