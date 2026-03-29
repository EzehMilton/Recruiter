package com.recruiter.domain;

public enum ShortlistQuality {
    EXCELLENT(90, "Excellent only"),
    VERY_GOOD(75, "Very good and above"),
    GOOD(60, "Good and above"),
    ALL(40, "All viable candidates");

    private final int threshold;
    private final String label;

    ShortlistQuality(int threshold, String label) {
        this.threshold = threshold;
        this.label = label;
    }

    public int getThreshold() {
        return threshold;
    }

    public String getLabel() {
        return label;
    }
}
