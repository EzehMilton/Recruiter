package com.recruiter.service;

import com.recruiter.document.ExtractedDocument;
import com.recruiter.domain.CandidateProfile;

public interface CandidateProfileFactory {

    CandidateProfile create(ExtractedDocument extractedDocument);
}
