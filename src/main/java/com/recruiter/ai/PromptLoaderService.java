package com.recruiter.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads fit-assessor system prompts from {@code classpath:prompts/<key>.txt}.
 * Results are cached after first load. If a sector-specific file is missing the
 * service falls back to the generic prompt. If the generic file itself is missing
 * the application fails fast at startup.
 */
@Service
public class PromptLoaderService {

    private static final Logger log = LoggerFactory.getLogger(PromptLoaderService.class);
    private static final String PREFIX = "prompts/";
    private static final String SUFFIX = ".txt";

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String load(String promptKey) {
        return cache.computeIfAbsent(promptKey, this::doLoad);
    }

    private String doLoad(String promptKey) {
        String path = PREFIX + promptKey + SUFFIX;
        ClassPathResource resource = new ClassPathResource(path);

        if (!resource.exists()) {
            String genericKey = Sector.GENERIC.getPromptKey();
            if (genericKey.equals(promptKey)) {
                throw new IllegalStateException(
                        "Generic prompt file is missing from the classpath: " + path
                                + ". This is a required configuration file.");
            }
            log.warn("Prompt file '{}' not found — falling back to generic prompt.", path);
            return load(genericKey);
        }

        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            log.debug("Loaded prompt '{}' ({} chars)", path, content.length());
            return content;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read prompt file: " + path, e);
        }
    }
}
