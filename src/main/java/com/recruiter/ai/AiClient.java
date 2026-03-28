package com.recruiter.ai;

public interface AiClient {

    <T> T generateStructuredObject(AiStructuredRequest request, Class<T> responseType);
}
