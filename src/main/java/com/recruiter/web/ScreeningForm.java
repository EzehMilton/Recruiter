package com.recruiter.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Getter
@Setter
public class ScreeningForm {

    @NotBlank(message = "Job description is required")
    private String jobDescription;

    @NotNull(message = "Shortlist count is required")
    @Min(value = 1, message = "Shortlist count must be at least 1")
    private Integer shortlistCount = 3;

    private List<MultipartFile> cvFiles;
}