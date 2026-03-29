package com.recruiter.service;

public record ScreeningProgressEvent(
        String phase,
        int completed,
        int total,
        String candidateName,
        String message
) {
}
