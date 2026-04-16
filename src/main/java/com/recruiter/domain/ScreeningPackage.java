package com.recruiter.domain;

import java.util.Locale;

public enum ScreeningPackage {
    QUICK_SCREEN("Quick Screen"),
    STANDARD_SCREEN("Standard Screen"),
    PREMIUM_PACK("Premium Pack");

    private final String label;

    ScreeningPackage(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ScreeningPackage fromString(String value) {
        if (value == null || value.isBlank()) {
            return QUICK_SCREEN;
        }

        String normalized = value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);

        for (ScreeningPackage screeningPackage : values()) {
            if (screeningPackage.name().equals(normalized)) {
                return screeningPackage;
            }
        }
        return QUICK_SCREEN;
    }
}
