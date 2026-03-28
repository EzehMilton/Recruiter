package com.recruiter.prompt;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptLoaderTest {

    private final PromptLoader promptLoader = new PromptLoader(new DefaultResourceLoader());

    @Test
    void loadsPromptFromClasspath() {
        String prompt = promptLoader.load("extract-job-profile.txt");

        assertThat(prompt).contains("structured job description profile");
        assertThat(prompt).contains("extracted skills");
    }

    @Test
    void rejectsMissingPrompt() {
        assertThatThrownBy(() -> promptLoader.load("missing-prompt.txt"))
                .isInstanceOf(PromptLoadingException.class)
                .hasMessageContaining("Prompt file not found");
    }

    @Test
    void rejectsPathTraversalStylePromptNames() {
        assertThatThrownBy(() -> promptLoader.load("../extract-job-profile.txt"))
                .isInstanceOf(PromptLoadingException.class)
                .hasMessageContaining("simple file name");
    }
}
