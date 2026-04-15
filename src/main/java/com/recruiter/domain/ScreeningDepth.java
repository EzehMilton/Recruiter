package com.recruiter.domain;

public enum ScreeningDepth {
    FAST(10, "Fast (Top 10 candidates)"),
    BALANCED(20, "Balanced (Top 20 candidates)"),
    THOROUGH(50, "Thorough (Top 50 candidates)");

    private final int analysisCap;
    private final String label;

    ScreeningDepth(int analysisCap, String label) {
        this.analysisCap = analysisCap;
        this.label = label;
    }

    public int getAnalysisCap() {
        return analysisCap;
    }

    public String getLabel() {
        return label;
    }
}