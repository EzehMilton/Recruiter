package com.recruiter.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.Map;

@ControllerAdvice(assignableTypes = HomeController.class)
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final HomePageModelSupport homePageModelSupport;

    @ExceptionHandler(Exception.class)
    public Object handleUnexpectedException(Exception ex,
                                            HttpServletRequest request,
                                            Model model) {
        log.error("Unexpected screening request failure", ex);

        if (isCausedByMaxUploadSize(ex)) {
            return uploadTooLargeResponse(request, model);
        }

        if (isCausedByMultipartException(ex)) {
            return multipartUploadResponse(request, model);
        }

        if (isStreamingRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message",
                            "The screening request could not be completed due to an unexpected error. Please try again."));
        }

        homePageModelSupport.populateFreshForm(model);
        model.addAttribute("errorMessage",
                "The screening request could not be completed due to an unexpected error. Please try again.");
        return "index";
    }

    private Object uploadTooLargeResponse(HttpServletRequest request, Model model) {
        if (isStreamingRequest(request)) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", homePageModelSupport.maxUploadSizeMessage()));
        }

        homePageModelSupport.populateFreshForm(model);
        model.addAttribute("errorMessage", homePageModelSupport.maxUploadSizeMessage());
        return "index";
    }

    private Object multipartUploadResponse(HttpServletRequest request, Model model) {
        if (isStreamingRequest(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", homePageModelSupport.multipartUploadMessage()));
        }

        homePageModelSupport.populateFreshForm(model);
        model.addAttribute("errorMessage", homePageModelSupport.multipartUploadMessage());
        return "index";
    }

    private boolean isStreamingRequest(HttpServletRequest request) {
        return request.getRequestURI() != null && request.getRequestURI().startsWith("/analyse/stream");
    }

    private boolean isCausedByMaxUploadSize(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof MaxUploadSizeExceededException
                    || current instanceof FileSizeLimitExceededException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isCausedByMultipartException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof MultipartException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
