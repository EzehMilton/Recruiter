package com.recruiter.orchestration.embabel;

import java.util.Objects;

public record JobDescriptionText(String value) {

    public JobDescriptionText {
        value = Objects.requireNonNullElse(value, "").trim();
    }
}
