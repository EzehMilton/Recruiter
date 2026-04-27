package com.recruiter.web;

import com.recruiter.ai.Sector;
import com.recruiter.config.RecruitmentProperties;
import com.recruiter.domain.ScreeningPackage;
import com.recruiter.domain.ScreeningResult;
import com.recruiter.domain.ScreeningRunResult;
import com.recruiter.domain.ScoringMode;
import com.recruiter.service.CandidateScreeningFacade;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

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
                homePageModelSupport,
                new RerunStore(),
                null,
                new JdReviewStore(),
                new DirectExecutorService()
        );
        ExtendedModelMap model = new ExtendedModelMap();

        String viewName = controller.home("max-size", null, false, model);

        assertThat(viewName).isEqualTo("index");
        assertThat(model.get("errorMessage"))
                .isEqualTo("One or more uploaded CVs exceed the maximum size of 1 KB.");
        assertThat(model.get("maxFileSizeLabel")).isEqualTo("1 KB");

        ScreeningForm screeningForm = (ScreeningForm) model.get("screeningForm");
        assertThat(screeningForm).isNotNull();
        assertThat(screeningForm.getShortlistCount()).isEqualTo(4);
    }

    @Test
    void analyseAddsSectorDisplayToResultsModel() {
        RecruitmentProperties properties = new RecruitmentProperties();
        properties.afterPropertiesSet();
        HomePageModelSupport homePageModelSupport = new HomePageModelSupport(properties);
        CandidateScreeningFacade facade = new CandidateScreeningFacade(
                null, null, null, null, null, null, null, null, null, null,
                new com.recruiter.ai.AiModelSelectionService(new com.recruiter.config.AiModelRoutingProperties()),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(),
                null, null, null
        ) {
            @Override
            public ScreeningRunResult screen(String jobDescription, Integer shortlistCount, Double minimumShortlistScore,
                                             String requestedScoringMode, List<org.springframework.web.multipart.MultipartFile> cvFiles,
                                             ScreeningPackage screeningPackage,
                                             String requestedSector, Integer overrideAnalysisCap) {
                return new ScreeningRunResult(
                        4L,
                        3,
                        ScoringMode.ai,
                        Sector.HEALTHCARE,
                        31,
                        0,
                        0,
                        0,
                        28,
                        com.recruiter.ai.TokenUsage.ZERO,
                        null,
                        null,
                        new ScreeningResult(
                                new com.recruiter.domain.JobDescriptionProfile(jobDescription, List.of(), List.of(), null),
                                List.of()
                        )
                );
            }
        };
        HomeController controller = new HomeController(facade, null, homePageModelSupport,
                new RerunStore(), null, new JdReviewStore(), new DirectExecutorService());
        ExtendedModelMap model = new ExtendedModelMap();
        ScreeningForm form = new ScreeningForm();
        form.setJobDescription("Healthcare role");
        form.setShortlistCount(3);
        form.setShortlistQuality(com.recruiter.domain.ShortlistQuality.VERY_GOOD);
        form.setScoringMode("ai");
        form.setScreeningPackage(ScreeningPackage.STANDARD_SCREEN);
        form.setSector("HEALTHCARE");
        form.setCvFiles(List.of(new MockMultipartFile("cvFiles", "cv.pdf", "application/pdf", "x".getBytes())));

        String viewName = controller.analyse(form, new org.springframework.validation.BeanPropertyBindingResult(form, "screeningForm"), model);

        assertThat(viewName).isEqualTo("results");
        assertThat(model.get("sectorDisplay")).isEqualTo("Healthcare");
    }

    private static class DirectExecutorService extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
