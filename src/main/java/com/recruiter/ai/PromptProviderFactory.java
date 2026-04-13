package com.recruiter.ai;

import org.springframework.stereotype.Component;

/**
 * Resolves a {@link PromptProvider} for a given {@link Sector}.
 * Prompts are loaded lazily from {@code classpath:prompts/<sector-key>.txt}
 * and cached by {@link PromptLoaderService}. Unknown or null sectors fall back
 * to {@link Sector#GENERIC}.
 */
@Component
public class PromptProviderFactory {

    private final PromptLoaderService promptLoaderService;

    public PromptProviderFactory(PromptLoaderService promptLoaderService) {
        this.promptLoaderService = promptLoaderService;
    }

    public PromptProvider getProvider(Sector sector) {
        Sector resolved = sector != null ? sector : Sector.GENERIC;
        String systemPrompt = promptLoaderService.load(resolved.getPromptKey());
        return new LoadedPromptProvider(resolved, systemPrompt);
    }

    private record LoadedPromptProvider(Sector sector, String systemPrompt) implements PromptProvider {

        @Override
        public String getSystemPrompt() {
            return systemPrompt;
        }

        @Override
        public Sector getSector() {
            return sector;
        }
    }
}
