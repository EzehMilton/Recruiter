package com.recruiter.web;

import com.recruiter.config.RecruitmentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        RecruitmentProperties properties = new RecruitmentProperties();
        properties.setShortlistCount(3);
        properties.setMaxFileSizeBytes(1024);
        handler = new GlobalExceptionHandler(new HomePageModelSupport(properties));
    }

    @Test
    void wrappedUploadLimitErrorShowsSpecificHomepageMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/analyse");
        ExtendedModelMap model = new ExtendedModelMap();
        Exception ex = new IllegalStateException("Upload failed", new MaxUploadSizeExceededException(1024));

        Object response = handler.handleUnexpectedException(ex, request, model);

        assertThat(response).isEqualTo("index");
        assertThat(model.get("errorMessage"))
                .isEqualTo("One or more uploaded CVs exceed the maximum size of 1 KB.");
    }

    @Test
    void tomcatFileSizeLimitExceededShowsSpecificHomepageMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/analyse");
        ExtendedModelMap model = new ExtendedModelMap();
        var fileSizeEx = new FileSizeLimitExceededException("exceeds max", 6_000_000, 5_242_880);
        Exception ex = new org.apache.tomcat.util.http.InvalidParameterException(fileSizeEx);

        Object response = handler.handleUnexpectedException(ex, request, model);

        assertThat(response).isEqualTo("index");
        assertThat(model.get("errorMessage"))
                .isEqualTo("One or more uploaded CVs exceed the maximum size of 1 KB.");
    }

    @Test
    void tomcatFileSizeLimitExceededOnStreamReturnsJson413() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/analyse/stream");
        ExtendedModelMap model = new ExtendedModelMap();
        var fileSizeEx = new FileSizeLimitExceededException("exceeds max", 6_000_000, 5_242_880);
        Exception ex = new org.apache.tomcat.util.http.InvalidParameterException(fileSizeEx);

        Object response = handler.handleUnexpectedException(ex, request, model);

        assertThat(response).isInstanceOf(ResponseEntity.class);
        ResponseEntity<?> entity = (ResponseEntity<?>) response;
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(entity.getBody()).isEqualTo(java.util.Map.of(
                "message", "One or more uploaded CVs exceed the maximum size of 1 KB."
        ));
    }

    @Test
    void wrappedUploadLimitErrorOnStreamReturnsJson413() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/analyse/stream");
        ExtendedModelMap model = new ExtendedModelMap();
        Exception ex = new IllegalStateException("Upload failed", new MaxUploadSizeExceededException(1024));

        Object response = handler.handleUnexpectedException(ex, request, model);

        assertThat(response).isInstanceOf(ResponseEntity.class);
        ResponseEntity<?> entity = (ResponseEntity<?>) response;
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(entity.getBody()).isEqualTo(java.util.Map.of(
                "message", "One or more uploaded CVs exceed the maximum size of 1 KB."
        ));
    }
}
