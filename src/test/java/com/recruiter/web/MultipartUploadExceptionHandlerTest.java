package com.recruiter.web;

import com.recruiter.config.RecruitmentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MultipartUploadExceptionHandlerTest {

    private MultipartUploadExceptionHandler handler;

    @BeforeEach
    void setUp() {
        RecruitmentProperties properties = new RecruitmentProperties();
        properties.setMaxFileSizeBytes(1024);
        handler = new MultipartUploadExceptionHandler(new HomePageModelSupport(properties));
    }

    @Test
    void oversizedFormUploadRedirectsBackToHomePage() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/analyse");

        Object response = handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(1024), request);

        assertThat(response).isEqualTo("redirect:/?uploadError=max-size");
    }

    @Test
    void oversizedStreamingUploadReturnsJson413() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/analyse/stream");

        Object response = handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(1024), request);

        assertThat(response).isInstanceOf(ResponseEntity.class);
        ResponseEntity<?> entity = (ResponseEntity<?>) response;
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(entity.getBody()).isEqualTo(Map.of(
                "message", "One or more uploaded CVs exceed the maximum size of 1 KB."
        ));
    }

    @Test
    void multipartExceptionWithMaxUploadCauseUsesSameRedirect() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/analyse");
        MultipartException exception = new MultipartException(
                "Maximum upload size exceeded",
                new MaxUploadSizeExceededException(1024)
        );

        Object response = handler.handleMultipartException(exception, request);

        assertThat(response).isEqualTo("redirect:/?uploadError=max-size");
    }
}
