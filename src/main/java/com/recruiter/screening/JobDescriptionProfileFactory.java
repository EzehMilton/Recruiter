package com.recruiter.screening;

import com.recruiter.domain.JobDescriptionProfile;

public interface JobDescriptionProfileFactory {

    JobDescriptionProfile create(String jobDescriptionText);
}
