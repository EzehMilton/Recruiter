package com.recruiter.ai;

import com.fasterxml.jackson.databind.JsonNode;

public record AiStructuredRequest(
        String taskName,
        String instructions,
        String input,
        JsonNode schema
) {
}
