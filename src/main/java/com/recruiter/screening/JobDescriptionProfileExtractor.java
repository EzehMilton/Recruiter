package com.recruiter.screening;

import com.recruiter.domain.JobDescriptionProfile;

public interface JobDescriptionProfileExtractor {

    JobDescriptionProfile extract(String jobDescriptionText);
}
