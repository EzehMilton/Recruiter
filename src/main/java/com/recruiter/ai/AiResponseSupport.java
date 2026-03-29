package com.recruiter.ai;

import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

final class AiResponseSupport {

    private AiResponseSupport() {
    }

    static <T> AiResult<T> toAiResult(ResponseEntity<ChatResponse, T> responseEntity) {
        ChatResponse response = responseEntity.response();
        return new AiResult<>(
                responseEntity.entity(),
                response != null && response.getMetadata() != null
                        ? toTokenUsage(response.getMetadata().getUsage())
                        : TokenUsage.ZERO
        );
    }

    static TokenUsage toTokenUsage(Usage usage) {
        if (usage == null) {
            return TokenUsage.ZERO;
        }

        int promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        int completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
        int totalTokens = usage.getTotalTokens() != null ? usage.getTotalTokens() : promptTokens + completionTokens;
        return new TokenUsage(promptTokens, completionTokens, totalTokens);
    }
}
