package com.recruiter.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.Map;

@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class MultipartUploadExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(MultipartUploadExceptionHandler.class);

    private final HomePageModelSupport homePageModelSupport;

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("Screening upload failed: uploaded file exceeded configured max size", ex);
        return buildUploadTooLargeResponse(request);
    }

    @ExceptionHandler(MultipartException.class)
    public Object handleMultipartException(MultipartException ex, HttpServletRequest request) {
        log.warn("Screening upload failed: multipart request could not be processed", ex);

        if (isCausedByMaxUploadSize(ex)) {
            return buildUploadTooLargeResponse(request);
        }

        if (isStreamingRequest(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", homePageModelSupport.multipartUploadMessage()));
        }

        return "redirect:/?uploadError=multipart";
    }

    private Object buildUploadTooLargeResponse(HttpServletRequest request) {
        if (isStreamingRequest(request)) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", homePageModelSupport.maxUploadSizeMessage()));
        }
        return "redirect:/?uploadError=max-size";
    }

    private boolean isStreamingRequest(HttpServletRequest request) {
        return request.getRequestURI() != null && request.getRequestURI().startsWith("/analyse/stream");
    }

    private boolean isCausedByMaxUploadSize(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof MaxUploadSizeExceededException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
