package com.recruiter.screening;

import com.recruiter.document.ExtractedDocument;
import com.recruiter.domain.CandidateProfile;

public interface CandidateProfileExtractor {

    CandidateProfile extract(ExtractedDocument extractedDocument);
}
