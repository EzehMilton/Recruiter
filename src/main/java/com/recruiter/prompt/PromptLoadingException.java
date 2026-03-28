package com.recruiter.prompt;

public class PromptLoadingException extends RuntimeException {

    public PromptLoadingException(String message, Throwable cause) {
        super(message, cause);
    }

    public PromptLoadingException(String message) {
        super(message);
    }
}
