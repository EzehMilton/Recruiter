package com.recruiter.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Validated
@ConfigurationProperties(prefix = "recruitment.ai")
public class RecruitmentAiProperties {

    private boolean enabled = false;

    private String provider = "openai";

    private boolean fallbackToHeuristics = true;

    private final OpenAiProperties openai = new OpenAiProperties();

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public void setFallbackToHeuristics(boolean fallbackToHeuristics) {
        this.fallbackToHeuristics = fallbackToHeuristics;
    }

    @Getter
    public static class OpenAiProperties {

        private String baseUrl = "https://api.openai.com/v1";

        private String apiKey = "";

        private String model = "";

        private int maxOutputTokens = 800;

        private double temperature = 0.2;

        private Duration timeout = Duration.ofSeconds(30);

        private int maxAttempts = 3;

        private Duration retryBackoff = Duration.ofMillis(500);

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public void setMaxOutputTokens(int maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public void setRetryBackoff(Duration retryBackoff) {
            this.retryBackoff = retryBackoff;
        }
    }
}
