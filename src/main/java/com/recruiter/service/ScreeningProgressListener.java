package com.recruiter.service;

@FunctionalInterface
public interface ScreeningProgressListener {

    void onProgress(ScreeningProgressEvent event);
}
