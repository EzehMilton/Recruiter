package com.recruiter.web;

import com.recruiter.config.RecruitmentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import static org.assertj.core.api.Assertions.assertThat;

class HomeControllerTest {

    @Test
    void homeAddsUploadErrorMessageAndConfiguredFormMetadata() {
        RecruitmentProperties properties = new RecruitmentProperties();
        properties.setShortlistCount(4);
        properties.setMaxFileSizeBytes(1024);
        HomePageModelSupport homePageModelSupport = new HomePageModelSupport(properties);
        HomeController controller = new HomeController(
                null,
                null,
                homePageModelSupport
        );
        ExtendedModelMap model = new ExtendedModelMap();

        String viewName = controller.home("max-size", model);

        assertThat(viewName).isEqualTo("index");
        assertThat(model.get("errorMessage"))
                .isEqualTo("One or more uploaded CVs exceed the maximum size of 1 KB.");
        assertThat(model.get("maxFileSizeLabel")).isEqualTo("1 KB");

        ScreeningForm screeningForm = (ScreeningForm) model.get("screeningForm");
        assertThat(screeningForm).isNotNull();
        assertThat(screeningForm.getShortlistCount()).isEqualTo(4);
    }
}
