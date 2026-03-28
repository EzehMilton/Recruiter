package com.recruiter.prompt;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Component
public class PromptLoader {

    private static final String DEFAULT_PROMPT_BASE_PATH = "classpath:/prompts/";

    private final ResourceLoader resourceLoader;
    private final String promptBasePath;

    @Autowired
    public PromptLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader must not be null");
        this.promptBasePath = DEFAULT_PROMPT_BASE_PATH;
    }

    public String load(String promptFilename) {
        String sanitizedFilename = sanitizeFilename(promptFilename);
        Resource resource = resourceLoader.getResource(promptBasePath + sanitizedFilename);
        if (!resource.exists()) {
            throw new PromptLoadingException("Prompt file not found: " + sanitizedFilename);
        }

        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new PromptLoadingException("Failed to load prompt file: " + sanitizedFilename, ex);
        }
    }

    private String sanitizeFilename(String promptFilename) {
        if (promptFilename == null || promptFilename.isBlank()) {
            throw new PromptLoadingException("Prompt filename must not be blank");
        }

        String sanitized = promptFilename.trim();
        if (sanitized.contains("..") || sanitized.contains("/") || sanitized.contains("\\")) {
            throw new PromptLoadingException("Prompt filename must be a simple file name: " + promptFilename);
        }
        return sanitized;
    }
}
