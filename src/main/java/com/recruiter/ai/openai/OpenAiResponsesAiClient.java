package com.recruiter.ai.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.recruiter.ai.AiClient;
import com.recruiter.ai.AiClientException;
import com.recruiter.ai.AiStructuredRequest;
import com.recruiter.config.RecruitmentAiProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
public class OpenAiResponsesAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiResponsesAiClient.class);

    private final ObjectMapper objectMapper;
    private final RecruitmentAiProperties aiProperties;

    @Override
    public <T> T generateStructuredObject(AiStructuredRequest request, Class<T> responseType) {
        RecruitmentAiProperties.OpenAiProperties openAi = aiProperties.getOpenai();
        validateConfiguration(openAi);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = toTimeoutMillis(openAi.getTimeout());
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);

        RestClient restClient = RestClient.builder()
                .baseUrl(trimTrailingSlash(openAi.getBaseUrl()))
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + openAi.getApiKey().trim())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", openAi.getModel().trim());
        requestBody.put("instructions", request.instructions());
        requestBody.put("input", request.input());
        requestBody.put("max_output_tokens", openAi.getMaxOutputTokens());
        requestBody.put("temperature", openAi.getTemperature());
        requestBody.putObject("text")
                .putObject("format")
                .put("type", "json_schema")
                .put("name", request.taskName())
                .put("strict", true)
                .set("schema", request.schema());

        int maxAttempts = Math.max(1, openAi.getMaxAttempts());
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                JsonNode response = restClient.post()
                        .uri("/responses")
                        .body(requestBody)
                        .retrieve()
                        .body(JsonNode.class);

                String structuredJson = extractOutputText(response);
                return objectMapper.readValue(structuredJson, responseType);
            } catch (JsonProcessingException ex) {
                throw new AiClientException("Failed to parse structured AI response for task " + request.taskName(), ex);
            } catch (RestClientException ex) {
                if (!isTransient(ex) || attempt >= maxAttempts) {
                    log.warn("OpenAI call failed: task={}, attempt={}, transient={}, reason={}",
                            request.taskName(), attempt, isTransient(ex), summarize(ex));
                    throw new AiClientException("OpenAI request failed for task " + request.taskName(), ex);
                }

                log.warn("OpenAI transient failure, retrying: task={}, attempt={}/{}, backoffMs={}, reason={}",
                        request.taskName(),
                        attempt,
                        maxAttempts,
                        openAi.getRetryBackoff().toMillis(),
                        summarize(ex));
                sleepBackoff(openAi.getRetryBackoff());
            }
        }

        throw new AiClientException("OpenAI request failed for task " + request.taskName());
    }

    private void validateConfiguration(RecruitmentAiProperties.OpenAiProperties openAi) {
        if (!aiProperties.isEnabled()) {
            throw new AiClientException("AI integration is disabled");
        }
        if (!"openai".equalsIgnoreCase(aiProperties.getProvider())) {
            throw new AiClientException("Configured AI provider is not supported by the OpenAI client: " + aiProperties.getProvider());
        }
        if (openAi.getApiKey() == null || openAi.getApiKey().isBlank()) {
            throw new AiClientException("OpenAI API key is not configured");
        }
        if (openAi.getModel() == null || openAi.getModel().isBlank()) {
            throw new AiClientException("OpenAI model is not configured");
        }
    }

    private String extractOutputText(JsonNode response) {
        if (response == null || response.isNull()) {
            throw new AiClientException("OpenAI response body was empty");
        }

        JsonNode output = response.path("output");
        if (!output.isArray()) {
            throw new AiClientException("OpenAI response did not include output items");
        }

        for (JsonNode outputItem : output) {
            JsonNode content = outputItem.path("content");
            if (!content.isArray()) {
                continue;
            }

            for (JsonNode contentItem : content) {
                String type = contentItem.path("type").asText("");
                if ("refusal".equals(type)) {
                    throw new AiClientException("OpenAI model refused the request");
                }
                if ("output_text".equals(type)) {
                    String text = contentItem.path("text").asText("");
                    if (!text.isBlank()) {
                        return text;
                    }
                }
            }
        }

        throw new AiClientException("OpenAI response did not contain structured output text");
    }

    private boolean isTransient(RestClientException ex) {
        if (ex instanceof ResourceAccessException) {
            return true;
        }
        if (ex instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            return status == 408 || status == 429 || status >= 500;
        }
        return false;
    }

    private void sleepBackoff(java.time.Duration backoff) {
        try {
            Thread.sleep(Math.max(0L, backoff.toMillis()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiClientException("AI retry backoff was interrupted", ex);
        }
    }

    private int toTimeoutMillis(java.time.Duration timeout) {
        long millis = timeout == null ? 30_000L : timeout.toMillis();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, millis));
    }

    private String summarize(RestClientException ex) {
        if (ex instanceof RestClientResponseException responseException) {
            return "status=" + responseException.getStatusCode().value();
        }
        return ex.getClass().getSimpleName() + ": " + ex.getMessage();
    }

    private String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
