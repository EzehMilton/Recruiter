package com.recruiter.web;

import com.recruiter.domain.ScreeningRunResult;
import com.recruiter.service.CandidateScreeningFacade;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CandidateScreeningFacade candidateScreeningFacade;
    private final ScreeningFormValidator screeningFormValidator;
    private final HomePageModelSupport homePageModelSupport;
    private final RerunStore rerunStore;

    @InitBinder("screeningForm")
    public void initBinder(WebDataBinder binder) {
        binder.addValidators(screeningFormValidator);
    }

    @GetMapping("/")
    public String home(@RequestParam(name = "uploadError", required = false) String uploadError,
                       @RequestParam(name = "rerun", required = false) String rerunId,
                       Model model) {
        model.addAttribute("screeningForm", homePageModelSupport.newScreeningForm());
        homePageModelSupport.addFormConstants(model);
        applyUploadError(uploadError, model);
        if (rerunId != null && !rerunId.isBlank()) {
            model.addAttribute("rerunId", rerunId);
        }
        return "index";
    }

    @GetMapping("/rerun/{rerunId}")
    @ResponseBody
    public ResponseEntity<?> rerunData(@PathVariable String rerunId) {
        return rerunStore.get(rerunId)
                .map(snapshot -> {
                    List<Map<String, String>> files = snapshot.files().stream()
                            .map(f -> Map.of(
                                    "filename", f.filename(),
                                    "contentType", f.contentType(),
                                    "data", Base64.getEncoder().encodeToString(f.bytes())
                            ))
                            .toList();
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("jobDescription", snapshot.jobDescription());
                    body.put("files", files);
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/analyse")
    public String analyse(@Valid @ModelAttribute ScreeningForm screeningForm,
                          BindingResult bindingResult,
                          Model model) {
        int uploadedFileCount = countUploadedFiles(screeningForm.getCvFiles());
        log.info("Screening request started: uploadedFiles={}, requestedShortlistCount={}",
                uploadedFileCount, screeningForm.getShortlistCount());

        if (bindingResult.hasErrors()) {
            log.warn("Screening request validation failed: uploadedFiles={}, validationErrors={}",
                    uploadedFileCount, bindingResult.getErrorCount());
            homePageModelSupport.addFormConstants(model);
            return "index";
        }

        double minimumShortlistScore = (double) screeningForm.getShortlistQuality().getThreshold();
        int analysisCap = screeningForm.getScreeningDepth().getAnalysisCap();
        String rerunId = rerunStore.save(screeningForm.getJobDescription(), screeningForm.getCvFiles());
        ScreeningRunResult screeningRunResult = candidateScreeningFacade.screen(
                screeningForm.getJobDescription(),
                screeningForm.getShortlistCount(),
                minimumShortlistScore,
                screeningForm.getScoringMode(),
                screeningForm.getCvFiles(),
                screeningForm.getSector(),
                analysisCap
        );
        var screeningResult = screeningRunResult.screeningResult();

        model.addAttribute("screeningResult", screeningResult);
        model.addAttribute("batchId", screeningRunResult.batchId());
        model.addAttribute("shortlistCount", screeningRunResult.shortlistCount());
        model.addAttribute("scoringMode", screeningRunResult.effectiveScoringMode().name());
        model.addAttribute("sectorDisplay", screeningRunResult.sectorDisplay());
        model.addAttribute("totalCvsReceived", screeningRunResult.totalCvsReceived());
        model.addAttribute("duplicateCvsRemoved", screeningRunResult.duplicateCvsRemoved());
        model.addAttribute("duplicateSummary", screeningRunResult.duplicateSummary());
        model.addAttribute("candidatesScored", screeningRunResult.candidatesScored());
        model.addAttribute("wasReduced", screeningRunResult.wasReduced());
        model.addAttribute("aiUsageDisplay", screeningRunResult.aiUsageDisplay());
        model.addAttribute("shortlistQuality", screeningForm.getShortlistQuality());
        model.addAttribute("rerunId", rerunId);
        model.addAttribute("successMessage",
                buildSuccessMessage(screeningRunResult));
        log.info("Screening request completed: candidatesProcessed={}, shortlisted={}",
                screeningResult.candidateEvaluations().size(),
                screeningResult.shortlistedCandidates().size());
        return "results";
    }

    @PostMapping("/analyse/stream")
    public SseEmitter analyseWithProgress(@Valid @ModelAttribute ScreeningForm screeningForm,
                                          BindingResult bindingResult) {
        int uploadedFileCount = countUploadedFiles(screeningForm.getCvFiles());
        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onTimeout(() -> log.warn("SSE screening stream timed out"));
        emitter.onError(ex -> log.warn("SSE screening stream error: {}", ex.getMessage()));
        emitter.onCompletion(() -> log.debug("SSE screening stream completed"));

        if (bindingResult.hasErrors()) {
            log.warn("Streaming screening request validation failed: uploadedFiles={}, validationErrors={}",
                    uploadedFileCount, bindingResult.getErrorCount());
            startImmediateErrorStream(emitter, firstValidationMessage(bindingResult));
            return emitter;
        }

        ScreeningForm detachedForm;
        try {
            detachedForm = detachForm(screeningForm);
        } catch (IOException ex) {
            log.error("Unable to prepare uploaded files for streaming analysis", ex);
            startImmediateErrorStream(emitter, "Unable to prepare uploaded files for AI analysis.");
            return emitter;
        }

        Thread.startVirtualThread(() -> runStreamingScreening(detachedForm, emitter, uploadedFileCount));
        return emitter;
    }

    private String buildSuccessMessage(ScreeningRunResult result) {
        var screeningResult = result.screeningResult();
        int shortlisted = screeningResult.shortlistedCandidates().size();
        if (result.wasReduced()) {
            return "Received " + result.totalCvsReceived() + " CVs. "
                    + "Reduced to the top " + result.candidatesScored()
                    + " candidates using a fast first-pass relevance filter before full analysis. "
                    + shortlisted + " candidate(s) shortlisted.";
        }
        return "Analysed " + result.candidatesScored()
                + " CV(s) and selected " + shortlisted + " shortlisted candidate(s).";
    }

    private void runStreamingScreening(ScreeningForm screeningForm,
                                       SseEmitter emitter,
                                       int uploadedFileCount) {
        // Save before screening so file bytes are available before temp files are cleaned up.
        String rerunId = rerunStore.save(screeningForm.getJobDescription(), screeningForm.getCvFiles());
        try {
            trySendSseEvent(emitter, "progress", Map.of(
                    "phase", "starting",
                    "completed", 0,
                    "total", uploadedFileCount,
                    "message", "Preparing analysis..."
            ));

            double minimumShortlistScore = (double) screeningForm.getShortlistQuality().getThreshold();
            int analysisCap = screeningForm.getScreeningDepth().getAnalysisCap();
            ScreeningRunResult screeningRunResult = candidateScreeningFacade.screen(
                    screeningForm.getJobDescription(),
                    screeningForm.getShortlistCount(),
                    minimumShortlistScore,
                    screeningForm.getScoringMode(),
                    screeningForm.getCvFiles(),
                    event -> sendProgressEvent(emitter, event),
                    screeningForm.getSector(),
                    analysisCap
            );

            trySendSseEvent(emitter, "complete", Map.of(
                    "phase", "complete",
                    "batchId", screeningRunResult.batchId(),
                    "redirectUrl", "/history/" + screeningRunResult.batchId(),
                    "rerunId", rerunId,
                    "message", "Screening complete."
            ));
            emitter.complete();
        } catch (Exception ex) {
            log.error("Streaming screening request failed", ex);
            try {
                trySendSseEvent(emitter, "error", Map.of(
                        "phase", "error",
                        "message", "Screening failed: " + ex.getMessage()
                ));
                emitter.complete();
            } catch (Exception sendEx) {
                emitter.completeWithError(ex);
            }
        } finally {
            cleanupDetachedFiles(screeningForm.getCvFiles());
        }
    }

    private void sendProgressEvent(SseEmitter emitter, com.recruiter.service.ScreeningProgressEvent event) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("phase", event.phase());
            payload.put("completed", event.completed());
            payload.put("total", event.total());
            payload.put("message", event.message());
            if (event.candidateName() != null && !event.candidateName().isBlank()) {
                payload.put("candidateName", event.candidateName());
            }
            trySendSseEvent(emitter, "progress", payload);
        } catch (Exception ex) {
            log.debug("Skipping SSE progress update because the client is unavailable: {}", ex.getMessage());
        }
    }

    private void sendSseEvent(SseEmitter emitter, String eventName, Object payload) throws IOException {
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(toJson(payload)));
    }

    private void trySendSseEvent(SseEmitter emitter, String eventName, Object payload) {
        try {
            sendSseEvent(emitter, eventName, payload);
        } catch (Exception ex) {
            log.debug("Skipping SSE {} event because the client is unavailable: {}", eventName, ex.getMessage());
        }
    }

    private void startImmediateErrorStream(SseEmitter emitter, String message) {
        Thread.startVirtualThread(() -> {
            trySendSseEvent(emitter, "error", Map.of(
                    "phase", "error",
                    "message", message
            ));
            emitter.complete();
        });
    }

    private String toJson(Object payload) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(payload);
    }

    private String firstValidationMessage(BindingResult bindingResult) {
        return bindingResult.getAllErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Please fix the form errors.")
                .orElse("Please fix the form errors.");
    }

    private void applyUploadError(String uploadError, Model model) {
        if ("max-size".equals(uploadError)) {
            model.addAttribute("errorMessage", homePageModelSupport.maxUploadSizeMessage());
            return;
        }
        if ("multipart".equals(uploadError)) {
            model.addAttribute("errorMessage", homePageModelSupport.multipartUploadMessage());
        }
    }

    private ScreeningForm detachForm(ScreeningForm screeningForm) throws IOException {
        ScreeningForm detachedForm = new ScreeningForm();
        detachedForm.setJobDescription(screeningForm.getJobDescription());
        detachedForm.setShortlistCount(screeningForm.getShortlistCount());
        detachedForm.setShortlistQuality(screeningForm.getShortlistQuality());
        detachedForm.setScreeningDepth(screeningForm.getScreeningDepth());
        detachedForm.setScoringMode(screeningForm.getScoringMode());
        detachedForm.setSector(screeningForm.getSector());
        detachedForm.setCvFiles(detachFiles(screeningForm.getCvFiles()));
        return detachedForm;
    }

    private List<MultipartFile> detachFiles(List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        List<MultipartFile> detached = new java.util.ArrayList<>();
        try {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String suffix = originalFilenameSuffix(file.getOriginalFilename());
                Path tempFile = Files.createTempFile("recruiter-screening-", suffix);
                try (InputStream inputStream = file.getInputStream()) {
                    Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
                }
                detached.add(new DetachedMultipartFile(file.getName(), file.getOriginalFilename(), file.getContentType(), tempFile));
            }
            return detached;
        } catch (IOException ex) {
            cleanupDetachedFiles(detached);
            throw ex;
        }
    }

    private void cleanupDetachedFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }

        for (MultipartFile file : files) {
            if (file instanceof DetachedMultipartFile detachedMultipartFile) {
                try {
                    Files.deleteIfExists(detachedMultipartFile.path());
                } catch (IOException ex) {
                    log.debug("Failed to delete detached upload {}", detachedMultipartFile.path(), ex);
                }
            }
        }
    }

    private String originalFilenameSuffix(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return ".upload";
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.'));
    }

    private int countUploadedFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return 0;
        }
        return (int) files.stream()
                .filter(file -> !file.isEmpty())
                .count();
    }

    private record DetachedMultipartFile(
            String name,
            String originalFilename,
            String contentType,
            Path path
    ) implements MultipartFile {

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            try {
                return Files.size(path) == 0;
            } catch (IOException ex) {
                return true;
            }
        }

        @Override
        public long getSize() {
            try {
                return Files.size(path);
            } catch (IOException ex) {
                return 0L;
            }
        }

        @Override
        public byte[] getBytes() throws IOException {
            return Files.readAllBytes(path);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            Files.copy(path, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public void transferTo(Path dest) throws IOException, IllegalStateException {
            Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public org.springframework.core.io.Resource getResource() {
            try {
                return new org.springframework.core.io.ByteArrayResource(getBytes()) {
                    @Override
                    public String getFilename() {
                        return originalFilename;
                    }
                };
            } catch (IOException ex) {
                return new org.springframework.core.io.ByteArrayResource(new byte[0]);
            }
        }
    }
}
