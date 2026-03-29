package com.recruiter.service;

import com.recruiter.domain.JobDescriptionProfile;

public interface JobDescriptionProfileFactory {

    JobDescriptionProfile create(String jobDescriptionText);
}
