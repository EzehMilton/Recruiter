package com.recruiter.ai;

import org.springframework.ai.chat.metadata.Usage;

import java.util.concurrent.atomic.AtomicInteger;

public class TokenUsageAccumulator {

    private final AtomicInteger promptTokens = new AtomicInteger(0);
    private final AtomicInteger completionTokens = new AtomicInteger(0);

    public void add(Usage usage) {
        add(AiResponseSupport.toTokenUsage(usage));
    }

    public void add(TokenUsage usage) {
        if (usage == null) {
            return;
        }
        promptTokens.addAndGet(usage.promptTokens());
        completionTokens.addAndGet(usage.completionTokens());
    }

    public TokenUsage toTokenUsage() {
        int prompt = promptTokens.get();
        int completion = completionTokens.get();
        return new TokenUsage(prompt, completion, prompt + completion);
    }
}
