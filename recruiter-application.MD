# Recruiter Application — Complete Reference

This document is the single source of truth for the Recruiter application. It covers what the application does, how it is built, every architectural pattern, how data flows from the browser through the backend and AI layer, how results are persisted and read back, and a step-by-step tutorial for building a similar application.

---

## Table of Contents

1. [What This Application Does](#1-what-this-application-does)
2. [Technology Stack](#2-technology-stack)
3. [Project Structure](#3-project-structure)
4. [Architecture Overview](#4-architecture-overview)
5. [Web Layer — UI to Backend](#5-web-layer--ui-to-backend)
6. [The Screening Pipeline](#6-the-screening-pipeline)
7. [Heuristic Scoring](#7-heuristic-scoring)
8. [AI Scoring](#8-ai-scoring)
9. [Sector Support](#9-sector-support)
10. [JD Quality Check Flow](#10-jd-quality-check-flow)
11. [Document Extraction](#11-document-extraction)
12. [Deduplication and Batch Reduction](#12-deduplication-and-batch-reduction)
13. [Domain Model](#13-domain-model)
14. [AI Data Models](#14-ai-data-models)
15. [Persistence Layer](#15-persistence-layer)
16. [Analytics](#16-analytics)
17. [Report Generation](#17-report-generation)
18. [In-Memory Stores](#18-in-memory-stores)
19. [Configuration Reference](#19-configuration-reference)
20. [HTTP Endpoints and Pages](#20-http-endpoints-and-pages)
21. [Error Handling and Degradation](#21-error-handling-and-degradation)
22. [Test Coverage](#22-test-coverage)
23. [Extension Points](#23-extension-points)
24. [Key Patterns Reference](#24-key-patterns-reference)
25. [Building a Similar Application — Tutorial](#25-building-a-similar-application--tutorial)

---

## 1. What This Application Does

Recruiter is a web-based candidate screening tool. A recruiter pastes a job description, uploads candidate CVs as PDF or Word files, chooses a scoring mode and shortlist criteria, and gets a ranked shortlist with match quality labels, structured assessments, and suggested interview questions.

The application supports two scoring paths:

- **AI mode** — uses OpenAI (via Spring AI) to extract structured job and candidate profiles, then performs a bounded fit assessment. The LLM never chooses a numeric score — Java code maps the AI assessment into a stable internal score using configurable dimension weights. AI candidates are scored in parallel using virtual threads, with live progress streamed to the browser via Server-Sent Events.
- **Heuristic mode** — uses local text matching against a cross-domain skill dictionary loaded from an external YAML file, supplemented by sector-specific terms when a sector is selected. Fully deterministic; works without any API key.

Both paths produce an internal 0–100 score used for ranking and shortlist eligibility. This score is never shown to the recruiter. Instead, candidates are labelled with a human-readable match label (Excellent / Strong / Good / Partial / Weak). The recruiter selects a shortlist size and a quality threshold from a dropdown. Only candidates meeting both criteria are shortlisted.

**End-to-end pipeline:**

```
User pastes JD + uploads CVs
        │
        ▼
JD quality assessment (AI, optional gate — blocks if score < 60)
        │
        ▼
Extract text from PDF/Word files
        │
        ▼
Deduplicate CVs (exact SHA-256 + near-duplicate Jaccard similarity)
        │
        ▼
Pre-filter to analysis cap (heuristic fast-pass when uploads exceed cap)
        │
        ▼
Extract structured profiles from JD and each CV (AI or heuristic)
        │
        ▼
Assess fit for each candidate against JD (AI or heuristic)
        │
        ▼
Score, rank, and shortlist
        │
        ▼
Persist to database
        │
        ▼
Render ranked shortlist with AI commentary
```

---

## 2. Technology Stack

| Component | Technology | Version |
|---|---|---|
| Runtime | Java | 21 |
| Framework | Spring Boot | 4.0.0 |
| AI Client | Spring AI | 2.0.0-M4 |
| AI Provider | OpenAI API | gpt-4o-mini (configurable per package) |
| Templating | Thymeleaf | Bundled with Spring Boot |
| ORM | Spring Data JPA / Hibernate | Bundled with Spring Boot |
| Database | H2 (dev, PostgreSQL-compatible mode) | Bundled with Spring Boot |
| PDF extraction | Apache PDFBox | 3.0.7 |
| Word extraction | Apache POI | 5.4.1 |
| JSON | Jackson | Bundled with Spring Boot |
| CSS | Bootstrap 5.3 (form pages); custom CSS (read-only pages) | — |
| Concurrency | JDK virtual threads | Java 21 feature |
| Build | Maven | — |

**Java 21 features actively used:**

- **Virtual threads** — parallel AI candidate evaluation with no pool sizing
- **Records** — all domain objects, DTOs, AI response models, and configuration nested classes
- **Pattern matching** (`instanceof Number n ? n.longValue()`) — safe type coercion from JPQL aggregate results
- **Switch expressions** — in service and formatting code

---

## 3. Project Structure

```
src/main/java/com/recruiter/
│
├── ai/                         # Everything AI-related
│   ├── *AiExtractor            # Interfaces and Spring AI implementations
│   ├── *AiService              # Fit assessment and JD quality assessment
│   ├── Ai*Profile              # AI data model records (JD profile, candidate profile)
│   ├── AiFitAssessment         # Assessment output from the AI
│   ├── JdQualityAssessment     # JD quality gate output from the AI
│   ├── AiResult.java           # Generic wrapper: typed result + token usage
│   ├── TokenUsage.java         # Prompt/completion/total token counts
│   ├── TokenUsageAccumulator   # Thread-safe accumulator for parallel calls
│   ├── AiResponseSupport       # Converts Spring AI ResponseEntity to AiResult<T>
│   ├── AiServiceConfiguration  # Conditional bean registration (requires API key)
│   ├── JdQualityFallbackConfiguration  # NoOp fallback when AI is absent
│   ├── AiModelSelectionService # Routes to correct model per screening package
│   ├── PromptProviderFactory   # Loads sector-specific system prompts from files
│   ├── SectorSkillDictionary   # Sector-specific keyword lists for heuristic mode
│   └── Sector.java             # Enum: 11 supported industry sectors
│
├── config/                     # Spring configuration classes
│   ├── RecruitmentProperties   # All application settings (prefix: recruitment)
│   ├── AiModelRoutingProperties # Per-package model routing
│   ├── SkillDictionaryProperties # External skill dictionary config
│   ├── ConcurrencyConfiguration # Virtual thread executor bean
│   └── MultipartConfig         # Tomcat part count override
│
├── domain/                     # Pure domain records — no JPA, no Spring
│   ├── CandidateProfile        # Extracted candidate data
│   ├── CandidateEvaluation     # Scored + assessed candidate
│   ├── CandidateScoreBreakdown # Score split across dimensions
│   ├── JobDescriptionProfile   # Extracted JD data
│   ├── ScreeningResult         # All evaluations for a run
│   ├── ScreeningRunResult      # Full metadata about a completed run
│   ├── ScreeningPackage        # Enum: QUICK_SCREEN, STANDARD_SCREEN, PREMIUM_PACK
│   ├── ScoringMode             # Enum: ai, heuristic, ai_with_fallbacks
│   ├── ShortlistQuality        # Enum: EXCELLENT(90), VERY_GOOD(75), GOOD(60), ALL(40)
│   └── ScreeningDepth          # Enum: FAST(10), BALANCED(20), THOROUGH(50)
│
├── document/                   # CV file handling
│   ├── CvTextExtractionService # Orchestrates extraction across file types
│   ├── PdfDocumentExtractionService  # PDFBox-based PDF extraction
│   ├── WordDocumentExtractionService # POI-based .doc/.docx extraction
│   ├── UploadedCvValidationService   # Pre-upload validation (type, size, count)
│   ├── ExtractedDocument       # Record: filename + raw text
│   └── DocumentExtractionOutcome     # Record: success or failure outcome
│
├── persistence/                # JPA + data access layer
│   ├── ScreeningBatchEntity    # Main JPA entity (one per screening run)
│   ├── CandidateEvaluationEntity # Child entity (one per candidate)
│   ├── EliminatedCandidateEntity # Child entity (pre-filtered candidates)
│   ├── ScreeningBatchRepository # Spring Data repository with custom queries
│   ├── ScreeningBatchPersistenceService # Writes screening results to DB
│   ├── ScreeningHistoryService  # Reads and reconstructs results from DB
│   ├── AnalyticsService        # Computes aggregated metrics from DB
│   ├── AnalyticsSummary        # Record: all analytics data + display helpers
│   ├── AiUsageSummary          # Record: aggregate token/cost totals
│   ├── ScreeningBatchHistoryItem # Lightweight DTO for history list
│   ├── StoredScreeningBatchResult # Full reconstructed batch for detail pages
│   ├── StoredCandidateDetail   # Single candidate for detail page
│   └── StoredEliminatedCandidate # Pre-filtered candidate DTO
│
├── report/                     # AI-powered narrative generation
│   ├── ReportNarrativeService  # Interface for batch report
│   ├── CandidateReportNarrativeService # Interface for per-candidate report
│   ├── SpringAiReportNarrativeService  # AI implementation
│   ├── SpringAiCandidateReportNarrativeService # AI implementation
│   ├── FallbackReportNarrativeService  # Template fallback when AI absent
│   ├── FallbackCandidateReportNarrativeService # Template fallback
│   ├── ReportNarrative         # Record: batch report sections
│   └── CandidateReportNarrative # Record: per-candidate narrative + interview Qs
│
├── service/                    # Business logic
│   ├── CandidateScreeningFacade # Orchestrates the full pipeline (main entry point)
│   ├── CandidateScoringService # Heuristic scoring logic
│   ├── TextProfileHeuristicsService # Skill/keyword/experience extraction
│   ├── HeuristicCandidateProfileFactory # Builds CandidateProfile from CV text
│   ├── HeuristicJobDescriptionProfileFactory # Builds JobDescriptionProfile from JD text
│   ├── CvDeduplicationService  # Exact (SHA-256) + near-duplicate (Jaccard) detection
│   ├── ShortlistService        # Resolves count/threshold; marks shortlisted candidates
│   ├── RankingService          # Sorts evaluations by score desc, name asc
│   ├── PipelineTimer           # Tracks wall-clock duration per pipeline phase
│   ├── ScreeningProgressEvent  # Record: phase/count/message for SSE updates
│   └── ScreeningProgressListener # @FunctionalInterface for progress callbacks
│
├── support/
│   └── BatchMetricsFormatter   # Static helpers: format cost, tokens, duration
│
└── web/                        # HTTP layer
    ├── HomeController          # Main form, SSE stream, JD review, rerun
    ├── HistoryController       # History list, batch detail, candidate detail, reports
    ├── AnalyticsController     # Analytics dashboard page
    ├── ScreeningForm           # Form binding object (Lombok @Getter @Setter)
    ├── ScreeningFormValidator  # Custom Spring Validator
    ├── HomePageModelSupport    # Builds shared model attributes for the index page
    ├── RerunStore              # In-memory snapshot store for form reruns
    └── JdReviewStore           # In-memory store for JD review state

src/main/resources/
├── application.yml             # All application configuration
├── skills.yml                  # External skill dictionary
├── templates/                  # Thymeleaf HTML templates
│   ├── index.html              # Main screening form
│   ├── results.html            # Shortlist results
│   ├── candidate-detail.html   # Individual candidate evaluation
│   ├── candidate-report.html   # AI-generated candidate narrative
│   ├── eliminated.html         # Pre-filtered candidates
│   ├── report.html             # Full batch report narrative
│   ├── history.html            # Batch history list
│   ├── analytics.html          # Analytics dashboard
│   ├── jd-review.html          # JD quality review page
│   └── loading.html            # Redirect/loading state
└── prompts/                    # Sector-specific AI system prompts (11 files)
    ├── generic.txt
    ├── it_and_technology.txt
    ├── healthcare.txt
    └── ... (one file per sector)
```

---

## 4. Architecture Overview

The application follows a layered architecture with an optional AI layer that sits alongside the service layer, injectable and replaceable.

```
┌─────────────────────────────────────────────┐
│                  Web Layer                   │
│   Controllers · Form Objects · Validators   │
│             Thymeleaf Templates              │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│               Service Layer                  │
│   CandidateScreeningFacade (orchestrator)   │
│   Scoring · Dedup · Ranking · Shortlisting  │
└──────┬───────────────────────┬──────────────┘
       │                       │
┌──────▼──────┐   ┌────────────▼──────────────┐
│   AI Layer  │   │      Persistence Layer     │
│  Extractors │   │   JPA Entities · Repos     │
│  Assessors  │   │   Persistence Service      │
│  Narrators  │   │   History Service          │
│  (Optional) │   │   Analytics Service        │
└─────────────┘   └───────────────────────────┘
```

**Key design decisions:**

- **All AI services are optional.** They are registered as beans only when `OPENAI_API_KEY` is set. The service layer checks `Optional<SomeAiService>` presence at runtime, not startup, and falls back gracefully.
- **Domain records are pure Java.** The `domain/` package has no Spring, no JPA, no Jackson annotations. It can be tested without loading the application context.
- **The pipeline is a single method.** `CandidateScreeningFacade.screen()` is the one entry point. All callers — the sync controller, the SSE controller, and the "proceed with weak JD" endpoint — go through it.
- **Persistence is separate from results.** The pipeline returns a `ScreeningRunResult` in-memory domain record immediately. The persistence layer converts it to JPA entities separately. History reads reconstruct domain objects from stored entities.
- **The LLM never chooses a numeric score.** The AI returns bounded categorical assessments (enums). Java maps these to a numeric score deterministically using configurable weights.

---

## 5. Web Layer — UI to Backend

### Form Submission

The main screening form is a standard Spring MVC form bound to `ScreeningForm`. This is how a submission travels from browser to pipeline:

```
Browser POST /analyse (multipart/form-data)
  └─ HomeController.analyse()
       ├─ @Valid @ModelAttribute ScreeningForm
       ├─ ScreeningFormValidator.validate()        ← custom validator
       ├─ If errors → return "index" (re-render form)
       ├─ rerunStore.save()                        ← snapshot before doing anything
       ├─ assessJdQuality()                        ← optional AI pre-check
       ├─ If JD weak → redirect /jd-review/{id}
       └─ candidateScreeningFacade.screen()        ← main pipeline
            └─ return "results" with model
```

**`ScreeningForm`** fields:

```java
public class ScreeningForm {
    @NotBlank private String jobDescription;
    private Integer shortlistCount;
    private ShortlistQuality shortlistQuality;    // enum
    private ScreeningDepth screeningDepth;        // enum: FAST/BALANCED/THOROUGH
    private String scoringMode;                   // "ai" or "heuristic"
    private ScreeningPackage screeningPackage;    // enum: QUICK_SCREEN/STANDARD/PREMIUM
    private String sector;
    private List<MultipartFile> cvFiles;
}
```

**Custom validation (`ScreeningFormValidator`)** runs alongside bean annotations via `@InitBinder`:

```java
@InitBinder("screeningForm")
public void initBinder(WebDataBinder binder) {
    binder.addValidators(screeningFormValidator);
}
```

Validation checks: JD word count against `recruitment.max-job-description-words`, shortlist count vs. analysis cap, and file type/size/count via `UploadedCvValidationService`.

### SSE Streaming (AI Mode)

When the user selects AI mode, the browser intercepts the form submit and opens an SSE stream instead of a traditional POST:

```javascript
// index.html
screeningForm.addEventListener('submit', async function(event) {
    if (selectedMode.value !== 'ai') return; // allow normal POST for heuristic
    event.preventDefault();
    // POST to /analyse/stream as a streaming request
    const response = await fetch('/analyse/stream', {
        method: 'POST', body: new FormData(screeningForm),
        headers: { 'Accept': 'text/event-stream' }
    });
    // parse events manually — EventSource doesn't support POST
});
```

The backend uses `SseEmitter` and runs the pipeline on a virtual thread:

```java
@PostMapping("/analyse/stream")
public SseEmitter analyseWithProgress(ScreeningForm form, BindingResult result) {
    SseEmitter emitter = new SseEmitter(300_000L);
    Thread.startVirtualThread(() -> runStreamingScreening(form, emitter));
    return emitter;  // returns immediately; pipeline runs asynchronously
}
```

The streaming path calls the same `candidateScreeningFacade.screen()` with an additional `ScreeningProgressListener` callback:

```java
candidateScreeningFacade.screen(
    ...,
    event -> sendProgressEvent(emitter, event),  // lambda satisfies @FunctionalInterface
    ...
)
```

When complete, a `complete` event carries the redirect URL:

```java
trySendSseEvent(emitter, "complete", Map.of(
    "redirectUrl", "/history/" + batchId,
    "batchId",   batchId,
    "rerunId",   rerunId
));
emitter.complete();
```

The browser reads the redirect URL and calls `window.location.href = payload.redirectUrl`.

SSE event types:

| Event | Data |
|---|---|
| `progress` | Phase, completed count, total, candidate name, message |
| `complete` | Batch ID, redirect URL, rerunId |
| `error` | Error message |

### Rerun Pre-population

After every screening, `RerunStore.save()` snapshots the job description text and CV file bytes. The resulting `rerunId` is embedded in the results page. When a recruiter clicks **Rerun Screening**, they are sent to `GET /?rerun={rerunId}`. On page load, JavaScript fetches `GET /rerun/{rerunId}`, receives the job description and base64-encoded files, and restores them into the form fields without any re-upload.

If `?jdOnly=true` is appended (used by the JD review page's "Edit & Resubmit" button), the JS skips file restoration and only pre-fills the job description textarea.

### Templates

| Template | Route | Description |
|---|---|---|
| `index.html` | `GET /` | Screening form, SSE progress overlay, rerun pre-population |
| `results.html` | `POST /analyse`, `GET /history/{id}` | Ranked candidates, shortlist, AI usage, batch metadata |
| `candidate-detail.html` | `GET /history/{id}/candidates/{rank}` | AI strengths/gaps/probes or heuristic score breakdown |
| `history.html` | `GET /history` | Batch list with AI cost and processing time |
| `eliminated.html` | `GET /history/{id}/eliminated` | Pre-filter eliminated candidates |
| `jd-review.html` | `GET /jd-review/{id}` | JD quality gate page with client message and two action buttons |
| `report.html` | `GET /history/{id}/report` | AI batch narrative — executive summary, interview Qs |
| `candidate-report.html` | `GET /history/{id}/candidates/{rank}/report` | AI per-candidate narrative |
| `analytics.html` | `GET /analytics` | Cost and latency dashboard |
| `loading.html` | `GET /loading` | Spinner; redirects to `?to=` param |

---

## 6. The Screening Pipeline

`CandidateScreeningFacade` in `com.recruiter.service` is the orchestrator. It coordinates all pipeline phases and is the single entry point for all callers.

### Pipeline Phases

```
Phase 1: resolve_settings
  ↓ Effective shortlist count, minimum score (from ShortlistQuality)
  ↓ Effective ScoringMode — ai / heuristic / ai_with_fallbacks
  ↓ Effective Sector (UI value → config default → GENERIC)
  ↓ Effective analysisCap (ScreeningDepth → recruitment.analysis-cap)

Phase 2: job_profile_extraction
  ↓ JobDescriptionProfileFactory.create(jdText)  → heuristic extraction
  ↓ applyHeuristicSectorSkills()                 → injects sector keywords

Phase 3: cv_text_extraction
  ↓ CvTextExtractionService.extractAll(files)
  ↓ Returns List<DocumentExtractionOutcome> — failures survive as zero-score

Phase 4: deduplication
  ↓ CvDeduplicationService.deduplicate()
  ↓ Exact: SHA-256 of normalised text
  ↓ Near-duplicate: Jaccard similarity > 0.85, same candidate name

Phase 5: ai_job_extraction  (AI mode only)
  ↓ JobDescriptionAiExtractor.extract(jdText)    → AiJobDescriptionProfile
  ↓ AiSkillExtractor.extract(jdText)             → ExtractedJobSkills (must-haves for pre-filter)
  ↓ Failure here → entire batch falls back to heuristic

Phase 6: pre_filter  (if readable candidates > analysisCap)
  ↓ CandidateScoringService.scoreForPrefilter()  → fast heuristic score per CV
  ↓ Keep top N by analysisCap + borderline rescue
  ↓ EliminatedCandidateSnapshot created for removed candidates

Phase 7: candidate_scoring
  AI path (parallel, one virtual thread per candidate):
    ↓ CandidateAiExtractor.extract(cvText)           → AiCandidateProfile
    ↓ FitAssessmentAiService.assess(jd, candidate, sectorPrompt)
    ↓ AiAssessmentToCandidateEvaluationMapper.map()  → CandidateEvaluation
    ↓ TokenUsageAccumulator.add(tokenUsage)
    ↓ If AI fails for one candidate → heuristic fallback for that candidate only

  Heuristic path (sequential):
    ↓ CandidateScoringService.evaluate(profile, jdProfile) → CandidateEvaluation

Phase 8: ranking_and_shortlisting
  ↓ RankingService.rank()          → sorted by score desc, name asc
  ↓ ShortlistService.shortlist()   → top N with score ≥ threshold
  ↓ ScreeningResult assembled

Phase 9: persistence
  ↓ ScreeningBatchPersistenceService.save()
  ↓ screeningBatchRepository.updateProcessingTime()  ← separate query after timing is done
  ↓ logPipelineSummary() — logs all phase durations at INFO level
```

### PipelineTimer

`PipelineTimer` tracks wall-clock time per named phase:

```java
PipelineTimer timer = new PipelineTimer();
timer.startPhase("cv_text_extraction");
// ... work ...
timer.startPhase("deduplication");  // automatically ends previous phase
// ... work ...
Map<String, Long> phases = timer.getPhaseDurations(); // {"cv_text_extraction": 234, ...}
long total = timer.totalElapsed();
```

Only the total is currently stored in the database (`processingTimeMs`). Per-phase breakdowns are visible in application logs by searching for `pipeline summary`.

### Parallel AI Evaluation

All candidates in AI mode are scored concurrently using virtual threads:

```java
ExecutorService executor = concurrencyConfig.screeningVirtualExecutor();
List<Future<CandidateEvaluation>> futures = candidates.stream()
    .map(c -> executor.submit(() -> evaluateCandidateWithAiFallback(c, ...)))
    .toList();

for (Future<CandidateEvaluation> future : futures) {
    CandidateEvaluation eval = future.get();
    tokenAccumulator.add(...);
}
```

Virtual threads are cheap. Each blocks on the OpenAI HTTP call without consuming a real OS thread. Hundreds of candidates can process concurrently with no pool exhaustion.

### Effective Scoring Mode

| Requested | AI Configured? | Job Extraction OK? | All Candidates OK? | Effective Mode |
|---|---|---|---|---|
| `ai` | Yes | Yes | Yes | `ai` |
| `ai` | Yes | Yes | Some fail | `ai_with_fallbacks` |
| `ai` | Yes | No | — | `heuristic` |
| `ai` | No | — | — | `heuristic` |
| `heuristic` | — | — | — | `heuristic` |

---

## 7. Heuristic Scoring

When AI is off (or as a per-candidate fallback), `CandidateScoringService` scores candidates purely through text analysis using five weighted components.

### Components and Weights

| Component | Default Max | What It Measures |
|---|---|---|
| Essential requirement fit | 40 | Required skills from the JD that appear in the CV |
| Broader skill fit | 25 (65 when no essentials) | All job skills found in the CV |
| Keyword support | 15 | Contextual keywords from requirement sections |
| Experience fit | 10 | Candidate years vs. required years |
| Gap penalty | −5 / −10 | Applied when candidate is missing essential skills |

All weights are configurable in `application.yml` under `recruitment.scoring.heuristic`. Weights must sum to 90 or defaults are used.

### Scoring Formula

```
// Essential fit (0 to 40)
essentialFit = (matched essential skills / total essential skills) × 40
// or 0 if no essential skills detected

// Broader skill fit
broaderMax   = hasEssentials ? 25 : 65
broaderFit   = (matched skills / total job skills) × broaderMax

// Keyword support (0 to 15)
keywordScore = (matched keywords / min(job keywords, 10)) × 15

// Experience fit (0 to 10)
experienceScore = min(candidate years / required years, 1.0) × 10

// Gap penalty
gapPenalty = −10  if candidate missing ALL essential skills (and ≥ 2 exist)
           = −5   if candidate missing more than half of essential skills
           = 0    otherwise

skillScore = clamp(essentialFit + broaderFit + gapPenalty, 0, 100)
total      = clamp(skillScore + keywordScore + experienceScore, 0, 100)
total      = rounded to 1 decimal
```

### Skill Dictionary

Skills are loaded from `classpath:skills.yml`. The file lists canonical skill names and alias mappings. If missing, `TextProfileHeuristicsService` falls back to hardcoded defaults covering: technical (Java, Python, AWS, Docker), healthcare (Nursing, Patient Care), trades, finance, education, hospitality, legal, logistics, creative, and general transferable skills.

`TextProfileHeuristicsService` also handles:
- Skill alias resolution (e.g. "JS" → "JavaScript")
- Experience extraction via `\b(\d{1,2})\+?\s+years?\b`
- Requirement classification into essential, desirable, unclassified
- Keyword extraction focused on requirement-dense lines

---

## 8. AI Scoring

### The Core Pattern: `responseEntity(Class<T>)`

Every AI service uses Spring AI's `ChatClient` to request structured JSON output and deserialise it directly into a typed Java record:

```java
// Inside SpringAiCandidateAiExtractor.extract()
return AiResponseSupport.toAiResult(
    chatClient.prompt()
        .system(SYSTEM_PROMPT)
        .options(OpenAiChatOptions.builder().model(model).build())
        .user(cvText)
        .call()
        .responseEntity(AiCandidateProfile.class)  // JSON → record
);
```

`AiResponseSupport.toAiResult()` wraps the result:

```java
public static <T> AiResult<T> toAiResult(ResponseEntity<ChatResponse, T> responseEntity) {
    ChatResponse response = responseEntity.response();
    return new AiResult<>(
        responseEntity.entity(),
        response != null ? toTokenUsage(response.getMetadata().getUsage()) : TokenUsage.ZERO
    );
}
```

This works because the system prompts explicitly instruct the model to return **only valid JSON** with a specific schema. No markdown fences, no prose.

### System Prompt Design

Every AI service holds a static `SYSTEM_PROMPT` constant following this structure:

1. **Role definition** — "You are a structured data extractor..."
2. **Task rules** — what to extract and how
3. **Output contract** — "Return ONLY valid JSON. No prose, no markdown fences."
4. **Schema** — the exact JSON structure expected

The four core service prompts (`SpringAi*`) are kept in Java source as constants because they are tightly coupled to the Java record they deserialise into. Sector-specific prompts (`prompts/*.txt`) only change what the AI emphasises in scoring criteria — they do not change the JSON schema, so they live in files and are loaded at runtime.

### AI Service Interfaces

Every AI capability is expressed as an interface:

```java
public interface CandidateAiExtractor {
    AiResult<AiCandidateProfile> extract(String cvText, ScreeningPackage pkg);
}

public interface FitAssessmentAiService {
    AiResult<AiFitAssessment> assess(
        AiJobDescriptionProfile jdProfile,
        AiCandidateProfile candidateProfile,
        String systemPrompt,
        ScreeningPackage pkg
    );
}

public interface JdQualityAssessorService {
    AiResult<JdQualityAssessment> assess(String jobDescriptionText);
}
```

### Conditional Bean Registration

`AiServiceConfiguration` is only active when an API key is present:

```java
@Configuration
@ConditionalOnExpression("'${spring.ai.openai.api-key:disabled}' != 'disabled'")
public class AiServiceConfiguration {
    @Bean CandidateAiExtractor candidateAiExtractor(ChatClient.Builder builder, ...) { ... }
    @Bean JdQualityAssessorService jdQualityAssessorService(ChatClient.Builder builder) { ... }
    // ... other AI beans
}
```

A separate fallback configuration registers NoOp beans when AI is absent:

```java
@Configuration
class JdQualityFallbackConfiguration {
    @Bean
    @ConditionalOnMissingBean(JdQualityAssessorService.class)
    JdQualityAssessorService jdQualityAssessorServiceFallback() {
        return jdText -> new AiResult<>(JdQualityAssessment.alwaysProceed(), TokenUsage.ZERO);
    }
}
```

Other AI services (`CandidateAiExtractor`, `FitAssessmentAiService`) are injected as `Optional<>` in the facade so the service layer can check presence at runtime:

```java
private final Optional<CandidateAiExtractor> candidateAiExtractor;

if (candidateAiExtractor.isPresent()) { /* AI path */ }
else { /* heuristic path */ }
```

### Mapping AI Assessment to a Numeric Score

`AiAssessmentToCandidateEvaluationMapper` converts the qualitative `AiFitAssessment` into a `double` score (0–100):

```
JudgementLevel → numeric value: STRONG=4, PARTIAL=3, WEAK=2, NONE=1 (null→2)

weightedSum = (essentialFit.level × 0.35)
            + (experienceFit.level × 0.25)
            + (desirableFit.level × 0.15)
            + (domainFit.level × 0.15)
            + (credentialsFit.level × 0.10)

baseScore   = ((weightedSum - 1.0) / 3.0) × 100

confidenceModifier: HIGH=1.00, MEDIUM=0.95, LOW=0.85

finalScore  = clamp(baseScore × confidenceModifier, 0, 100)
```

Weights are configurable per sector in `application.yml` under `recruitment.scoring.ai`.

**Legacy MatchBand fallback** (used only when all five dimension judgements are absent from the AI response):

| MatchBand | HIGH | MEDIUM | LOW |
|---|---|---|---|
| STRONG_MATCH | 90 | 85 | 80 |
| POSSIBLE_MATCH | 72 | 68 | 62 |
| WEAK_MATCH | 48 | 42 | 35 |
| NOT_RECOMMENDED | 20 | 15 | 10 |

### Token Usage Tracking

Every AI call returns an `AiResult<T>` with a `TokenUsage`. The `TokenUsageAccumulator` collects them thread-safely across parallel calls:

```java
class TokenUsageAccumulator {
    private final AtomicInteger promptTokens = new AtomicInteger();
    private final AtomicInteger completionTokens = new AtomicInteger();

    void add(TokenUsage usage) {
        promptTokens.addAndGet(usage.promptTokens());
        completionTokens.addAndGet(usage.completionTokens());
    }
}
```

At the end of the pipeline, the accumulated total estimates the batch cost:

```java
double costUsd = totalUsage.estimatedCostUsd(
    properties.getAiCost().getPromptPricePerMillion(),     // default: $0.15
    properties.getAiCost().getCompletionPricePerMillion()  // default: $0.60
);
```

### Model Selection per Package

`AiModelSelectionService` routes calls to the right model per screening package:

```yaml
recruitment:
  ai-models:
    quick-screen:
      screening-model: gpt-4o-mini
    premium-pack:
      screening-model: gpt-4o-mini   # upgrade to gpt-4o or gpt-5 in production
```

### Match Labels

| Internal Score | Label | Colour |
|---|---|---|
| 90+ | Excellent match | Dark green |
| 75–89.9 | Strong match | Green |
| 60–74.9 | Good match | Blue |
| 40–59.9 | Partial match | Amber |
| Below 40 | Weak match | Grey |

---

## 9. Sector Support

### How Sectors Work

Sectors affect both AI and heuristic scoring simultaneously:

- **AI mode** — `PromptProviderFactory` loads `classpath:prompts/<sector>.txt`, which is sent as the system prompt to the fit assessor. Each sector prompt emphasises different evidence signals and priorities.
- **Heuristic mode** — `SectorSkillDictionary.getSkills(sector)` returns a list of domain-specific terms injected into skill extraction for both the JD and CVs. Matching is symmetric — a term only scores if it appears in both.
- **Weight overrides** — each sector can override AI scoring dimension weights in `application.yml` under `recruitment.scoring.ai.sector-overrides.<sector>.*`.

### Current Sectors

| Enum value | UI Label | Prompt file |
|---|---|---|
| `GENERIC` | Default (Generic) | `generic.txt` |
| `IT_AND_TECHNOLOGY` | IT & Technology | `it_and_technology.txt` |
| `HEALTHCARE` | Healthcare | `healthcare.txt` |
| `FINANCE` | Finance | `finance.txt` |
| `EDUCATION` | Education | `education.txt` |
| `SALES_AND_MARKETING` | Sales & Marketing | `sales_and_marketing.txt` |
| `MANUAL_LABOUR` | Manual / Labour | `manual_labour.txt` |
| `RETAIL` | Retail | `retail.txt` |
| `CONSTRUCTION` | Construction | `construction.txt` |
| `MANUFACTURING` | Manufacturing | `manufacturing.txt` |
| `GREEN_ECONOMY` | Green Economy | `green_economy.txt` |

### Adding a New Sector

Adding a sector requires three changes. No other code changes are needed.

**Step 1 — Add the enum value (`Sector.java`):**

```java
MY_SECTOR("My Sector Label", "my_sector"),
```

The first argument is the UI dropdown label. The second is the prompt key — must match the filename in Step 2.

**Step 2 — Write the AI prompt file:**

Create `src/main/resources/prompts/my_sector.txt`. The file is the complete system prompt for the fit assessment stage. Follow the structure of existing files: role definition, sector-specific guidance block, then the core rules (identical across all prompts). Name the certifications or licences that are mandatory in this sector so the LLM treats their absence as a hard gap.

**Step 3 — Add heuristic skill terms (`SectorSkillDictionary.java`):**

```java
Map.entry(Sector.MY_SECTOR, List.of(
    "Term One", "Term Two", "Cert A", "Cert B"
)),
```

Include sector-specific certifications, tools, and systems not in the generic dictionary. Exclude generic transferable skills already covered.

Once in place, `PromptLoaderService` picks up the prompt automatically on first use, the sector dropdown updates with no template changes, and heuristic extraction is enriched automatically.

---

## 10. JD Quality Check Flow

Before screening is allowed to run, the AI optionally assesses the job description quality. If the score is below 60, the recruiter is redirected to a review page.

### Flow

```
POST /analyse
  ↓
HomeController.assessJdQuality(jd)   ← returns null if disabled or AI fails
  ↓
if (assessment.isWeak())  ← score < 60
  ↓
jdReviewStore.save()     ← stores assessment + all form params + rerunId
  ↓
redirect /jd-review/{id}
  ↓
jd-review.html           ← shows score badge, issues, client email message
  ↓
  ├── "Proceed Anyway" → POST /analyse/proceed/{id}
  │      ↓ loads JdReviewStore entry → loads CV bytes from RerunStore
  │      ↓ runs candidateScreeningFacade.screen() (no quality check)
  │
  └── "Edit & Resubmit" → GET /?rerun={rerunId}&jdOnly=true
         ↓ index.html with JD pre-filled, CVs NOT restored
```

The assessment returns a `JdQualityAssessment` record with a 0–100 score, issues categorised across clarity, missing requirements, overloaded areas, contradictions, and weak language, plus an AI-rewritten improved version and a pre-drafted client-facing email.

### Toggle

```yaml
recruitment:
  jd-quality-check:
    enabled: false   # set to false to bypass entirely
```

The check is fail-open — if the AI call throws, the gate is skipped and screening runs normally.

---

## 11. Document Extraction

`CvTextExtractionService` delegates to a list of `DocumentExtractionService` implementations, calling `supports(file)` on each until one matches:

```java
public List<DocumentExtractionOutcome> extractAll(List<MultipartFile> files) {
    return files.stream()
        .map(file -> extractors.stream()
            .filter(e -> e.supports(file))
            .findFirst()
            .map(e -> e.extract(file))
            .orElse(DocumentExtractionOutcome.unsupported(file.getOriginalFilename())))
        .toList();
}
```

**PDF** uses Apache PDFBox:

```java
try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
    return new PDFTextStripper().getText(doc);
}
```

**Word** uses Apache POI:

```java
// .docx (OOXML)
try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
    return new XWPFWordExtractor(doc).getText();
}
// .doc (OLE2)
try (HWPFDocument doc = new HWPFDocument(file.getInputStream())) {
    return new WordExtractor(doc).getText();
}
```

`DocumentExtractionOutcome` is a success/failure record:

```java
record DocumentExtractionOutcome(
    String originalFilename,
    ExtractedDocument extractedDocument,   // null on failure
    String failureMessage                  // null on success
)
```

Extraction failures remain in the pipeline as zero-score candidates — they don't abort the batch. Pre-upload validation (`UploadedCvValidationService`) checks file type, extension, size, and count before extraction begins.

---

## 12. Deduplication and Batch Reduction

### Deduplication

`CvDeduplicationService` runs two passes:

**Exact duplicates** — normalise text (lowercase, compress whitespace), compute SHA-256 hash, drop the file if the hash was already seen.

**Near-duplicates** — only compare files with the same normalised candidate name, then compute Jaccard similarity over token sets (common stop words excluded):

```
Jaccard(A, B) = |tokens(A) ∩ tokens(B)| / |tokens(A) ∪ tokens(B)|
```

If similarity ≥ 0.85, the shorter file is removed. Counts for exact and near-duplicates are tracked separately and shown to the recruiter.

### Pre-filter (Batch Reduction)

When readable CVs exceed the effective analysis cap:

1. All CVs are scored cheaply with heuristic matching.
2. The top N (the analysis cap) proceed guaranteed.
3. Borderline candidates within `prefilterBorderlineMargin` of the cutoff score, or those matching at least half of the job skills with some experience evidence, are rescued.
4. Extraction failures are appended back so they remain visible in results.
5. Eliminated readable candidates are persisted as `EliminatedCandidateEntity` with their pre-filter score and matched skills, viewable at `GET /history/{batchId}/eliminated`.

---

## 13. Domain Model

All classes in `com.recruiter.domain` are Java records — immutable, no framework dependencies, trivially testable.

### Core Records

**`CandidateProfile`** — what we know about a candidate before scoring:

```java
record CandidateProfile(
    String candidateName,
    String sourceFilename,
    String extractedText,         // full raw text (used by AI during scoring)
    List<String> extractedSkills,
    Integer yearsOfExperience     // null if not determinable
)
```

**`CandidateEvaluation`** — the result of scoring one candidate:

```java
record CandidateEvaluation(
    CandidateProfile candidateProfile,
    double score,                 // 0–100
    CandidateScoreBreakdown scoreBreakdown,
    String scoringPath,           // "ai", "heuristic", or "heuristic_fallback"
    String summary,
    boolean shortlisted,
    String aiConfidence,          // "HIGH", "MEDIUM", "LOW", or ""
    List<String> aiTopStrengths,
    List<String> aiTopGaps,
    List<String> aiInterviewProbeAreas
)
```

**`ScreeningRunResult`** — everything about a completed run:

```java
record ScreeningRunResult(
    Long batchId,
    int shortlistCount,
    ScoringMode effectiveScoringMode,
    Sector sector,
    int totalCvsReceived,
    int exactDuplicateCvsRemoved,
    int nearDuplicateCvsRemoved,
    int duplicateCvsRemoved,
    int candidatesScored,
    TokenUsage aiTokenUsage,
    Double aiEstimatedCostUsd,
    Long processingTimeMs,
    ScreeningResult screeningResult
)
```

### Enums with Behaviour

Domain enums carry data — not just names:

```java
public enum ShortlistQuality {
    EXCELLENT(90, "Excellent only"),
    VERY_GOOD(75, "Very good and above"),
    GOOD(60, "Good and above"),
    ALL(40, "All viable candidates");

    private final int threshold;  // minimum score to be shortlisted
    private final String label;
}

public enum ScreeningDepth {
    FAST(10, "Fast (Top 10 candidates)"),
    BALANCED(20, "Balanced (Top 20 candidates)"),
    THOROUGH(50, "Thorough (Top 50 candidates)");

    private final int analysisCap;
    private final String label;
}

public enum ScreeningPackage {
    QUICK_SCREEN("Quick Screen"),
    STANDARD_SCREEN("Standard Screen"),
    PREMIUM_PACK("Premium Pack");

    private final String label;
}
```

The controller reads `form.getShortlistQuality().getThreshold()` and `form.getScreeningDepth().getAnalysisCap()` directly — no switch statements, no magic numbers.

---

## 14. AI Data Models

These records define the JSON schema the AI model is expected to return. They are deserialised directly from the AI's JSON output via `responseEntity(Class<T>)`. All carry `@JsonIgnoreProperties(ignoreUnknown = true)` to tolerate extra AI output fields.

### `AiJobDescriptionProfile`

Structured extraction of what the job requires:

```java
record AiJobDescriptionProfile(
    String roleTitle,
    String roleFamily,
    String seniorityLevel,
    List<RequirementItem> essentialRequirements,   // type + importance per requirement
    List<RequirementItem> desirableRequirements,
    List<String> responsibilities,
    List<String> toolsMethodsOrSystems,
    List<String> qualificationsOrCertifications,
    List<String> domainContext,
    List<String> softSkills,
    List<String> workConditionsOrConstraints,
    String employmentType,
    String locationMode,
    ExtractionQuality extractionQuality,
    List<String> notesForRanking
)
```

Serialised and stored in `ScreeningBatchEntity.aiJobDescriptionProfileJson`.

### `AiCandidateProfile`

Structured extraction of a candidate's capabilities:

```java
record AiCandidateProfile(
    String candidateName,
    String headlineOrCurrentRole,
    String estimatedSeniorityLevel,
    Integer estimatedYearsOfRelevantExperience,
    List<EvidenceItem> demonstratedCapabilities,   // each with EvidenceStrength
    List<EvidenceItem> responsibilitiesPerformed,
    List<String> toolsMethodsOrSystems,
    List<String> qualificationsOrCertifications,
    List<String> domainExperience,
    ExtractionQuality extractionQuality,
    List<String> ambiguitiesOrMissingData
)
```

Serialised and stored in `CandidateEvaluationEntity.aiCandidateProfileJson`.

### `AiFitAssessment`

The AI's verdict on how well a candidate matches the job:

```java
record AiFitAssessment(
    MatchBand overallRecommendation,    // STRONG_MATCH | POSSIBLE_MATCH | WEAK_MATCH | NOT_RECOMMENDED
    ConfidenceLevel confidence,          // HIGH | MEDIUM | LOW
    DimensionJudgement essentialFit,    // each: JudgementLevel + rationale
    DimensionJudgement desirableFit,
    DimensionJudgement experienceFit,
    DimensionJudgement domainFit,
    DimensionJudgement credentialsFit,
    List<String> topStrengths,
    List<String> topGaps,
    List<String> interviewProbeAreas,
    String recruiterFacingExplanation
)
```

Serialised and stored in `CandidateEvaluationEntity.aiFitAssessmentJson`. The five dimension judgements are the primary input to score mapping. `topStrengths`, `topGaps`, and `interviewProbeAreas` are copied directly to `CandidateEvaluation`.

### `JdQualityAssessment`

Quality gate result for a job description:

```java
record JdQualityAssessment(
    Integer score,                // 0–100
    String rating,                // "STRONG" | "MODERATE" | "WEAK"
    String confidence,
    String processingDecision,    // "PROCEED" | "REVIEW_REQUIRED"
    String jobTitle,
    String summary,
    List<String> clarityIssues,
    List<String> missingRequirements,
    List<String> overloadedAreas,
    List<String> contradictions,
    List<String> weakLanguage,
    List<String> mustHaveSkills,
    List<String> niceToHaveSkills,
    List<String> risks,
    List<String> recommendations,
    String improvedVersion,       // AI-rewritten JD
    ClientMessage clientMessage   // subject + body for sending to the client
)
```

Not persisted. Stored temporarily in `JdReviewStore` (max 20 entries, LRU).

### AI Generic Wrapper

```java
record AiResult<T>(
    T result,
    TokenUsage tokenUsage    // defaults to TokenUsage.ZERO if null
)
```

Returned by every AI service method. Pairs the typed result with the call's token cost.

### AI Enums

| Enum | Values | Used In |
|---|---|---|
| `MatchBand` | `STRONG_MATCH`, `POSSIBLE_MATCH`, `WEAK_MATCH`, `NOT_RECOMMENDED` | `AiFitAssessment.overallRecommendation` |
| `ConfidenceLevel` | `HIGH`, `MEDIUM`, `LOW` | `AiFitAssessment.confidence` |
| `JudgementLevel` | `STRONG`, `PARTIAL`, `WEAK`, `NONE` | `DimensionJudgement.level` |
| `EvidenceStrength` | `STRONG`, `MODERATE`, `WEAK` | `EvidenceItem.evidenceStrength` |
| `RequirementType` | `SKILL`, `RESPONSIBILITY`, `TOOL_OR_SYSTEM`, `DOMAIN_EXPERIENCE`, `QUALIFICATION`, `CERTIFICATION`, `SOFT_SKILL`, `OTHER`, … | `RequirementItem.type` |
| `ImportanceLevel` | `MUST_HAVE`, `STRONG_PREFERENCE`, `NICE_TO_HAVE` | `RequirementItem.importance` |
| `ExtractionQuality` | `HIGH`, `MEDIUM`, `LOW` | Both AI profile records |

---

## 15. Persistence Layer

### Entity Hierarchy

Three JPA entities form a parent–child hierarchy:

```
ScreeningBatchEntity  (1 per screening run)
  ├── CandidateEvaluationEntity  (1 per candidate scored, cascade ALL)
  └── EliminatedCandidateEntity  (1 per pre-filtered candidate, cascade ALL)
```

All relationships use `cascade = ALL, orphanRemoval = true`. Adding children goes through the parent:

```java
// ScreeningBatchEntity
public void addCandidateEvaluation(CandidateEvaluationEntity eval) {
    eval.setScreeningBatch(this);
    this.candidateEvaluations.add(eval);
}
```

### `ScreeningBatchEntity` → table `screening_batches`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | Auto-generated |
| `created_at` | TIMESTAMP | Set on `@PrePersist`, never updated |
| `job_description_text` | TEXT | Full JD text |
| `shortlist_count` | INT | |
| `scoring_mode` | VARCHAR(30) | `ai`, `heuristic`, `ai_with_fallbacks` |
| `screening_package` | VARCHAR(40) | |
| `sector` | VARCHAR(50) | |
| `total_cvs_received` | INT | |
| `candidates_scored` | INT | |
| `shortlist_threshold` | DECIMAL | |
| `ai_job_description_profile_json` | TEXT | Serialised `AiJobDescriptionProfile` (nullable) |
| `prompt_versions` | VARCHAR | Comma-separated prompt version IDs |
| `ai_prompt_tokens` | INT | nullable |
| `ai_completion_tokens` | INT | nullable |
| `ai_total_tokens` | INT | nullable |
| `ai_estimated_cost_usd` | DECIMAL(8,4) | nullable |
| `processing_time_ms` | BIGINT | nullable; updated after save |

### `CandidateEvaluationEntity` → table `candidate_evaluations`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `screening_batch_id` | BIGINT FK | → `screening_batches.id` |
| `candidate_name` | VARCHAR | |
| `candidate_filename` | VARCHAR | |
| `extracted_skills` | TEXT | Newline-separated |
| `years_of_experience` | INT | nullable |
| `score` | DECIMAL(5,1) | 0–100 |
| `skill_score` | DECIMAL | Heuristic component |
| `keyword_score` | DECIMAL | Heuristic component |
| `experience_score` | DECIMAL | Heuristic component |
| `summary` | TEXT | |
| `scoring_path` | VARCHAR(30) | `ai`, `heuristic`, `heuristic_fallback` |
| `ai_candidate_profile_json` | TEXT | Serialised `AiCandidateProfile` (nullable) |
| `ai_fit_assessment_json` | TEXT | Serialised `AiFitAssessment` (nullable) |
| `rank_position` | INT | 1-based |
| `shortlisted` | BOOLEAN | |

### `EliminatedCandidateEntity` → table `eliminated_candidates`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `screening_batch_id` | BIGINT FK | |
| `candidate_name` | VARCHAR | |
| `candidate_filename` | VARCHAR | |
| `pre_filter_score` | DECIMAL | Heuristic score from first-pass |
| `matched_skills` | TEXT | Newline-separated matched skills |
| `score_label` | VARCHAR | |
| `elimination_reason` | VARCHAR | |

### Writing Results

`ScreeningBatchPersistenceService.save()` builds the entity graph from the domain result, then saves in one transaction (cascade covers children). Processing time is updated in a separate `@Modifying` query afterward, because the timer captures the end of persistence itself:

```java
Long batchId = persistenceSvc.save(...);   // persist, cascade children
long totalMs = timer.totalElapsed();
screeningBatchRepository.updateProcessingTime(batchId, totalMs);
```

### Reading Results Back

`ScreeningHistoryService` reconstructs domain objects from entity fields. The extracted text is not stored (too large and only needed during AI evaluation), so it is restored as an empty string on read-back:

```java
private CandidateEvaluation toCandidateEvaluation(CandidateEvaluationEntity entity) {
    CandidateProfile profile = new CandidateProfile(
        entity.getCandidateName(), entity.getCandidateFilename(),
        "",  // extractedText not stored
        splitSkills(entity.getExtractedSkills()),
        entity.getYearsOfExperience()
    );
    // parse aiFitAssessmentJson for AI fields (strengths, gaps, probes, confidence)
    return new CandidateEvaluation(profile, entity.getScore().doubleValue(), ...);
}
```

### Repository Queries

Custom JPQL queries in `ScreeningBatchRepository`:

```java
// All processing times for percentile calculation
@Query("select b.processingTimeMs from ScreeningBatchEntity b "
     + "where b.processingTimeMs is not null order by b.processingTimeMs asc")
List<Long> findAllProcessingTimesAsc();

// Aggregate AI usage for history page header
@Query("select coalesce(sum(b.aiTotalTokens), 0), "
     + "       coalesce(sum(b.aiEstimatedCostUsd), 0), count(b) "
     + "from ScreeningBatchEntity b where b.aiTotalTokens is not null")
Object[] findTotalAiUsage();

// Group by package for analytics
@Query("select b.screeningPackage, avg(b.aiEstimatedCostUsd), avg(b.processingTimeMs), count(b) "
     + "from ScreeningBatchEntity b where b.screeningPackage is not null "
     + "group by b.screeningPackage order by b.screeningPackage")
List<Object[]> findMetricsByPackage();
```

Aggregate results return `Object[]`. Values are extracted safely with pattern matching:

```java
Object[] row = repo.findTotalAiUsage();
long tokens = row[0] instanceof Number n ? n.longValue() : 0L;
BigDecimal cost = row[1] instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
```

### `@EntityGraph` for Selective Eager Loading

By default, `@OneToMany` collections are lazy. Pages that need child data use `@EntityGraph` to load them in one query:

```java
@EntityGraph(attributePaths = "candidateEvaluations")
Optional<ScreeningBatchEntity> findDetailedById(@Param("id") Long id);

@EntityGraph(attributePaths = "eliminatedCandidates")
Optional<ScreeningBatchEntity> findWithEliminatedCandidatesById(@Param("id") Long id);
```

Two separate methods (not one `@EntityGraph` covering both) avoids a Cartesian product join when both collections are large.

---

## 16. Analytics

`AnalyticsService.load()` runs five queries against the database and computes latency percentiles in Java (avoiding H2 compatibility issues with SQL `PERCENTILE_CONT`):

```java
private static Long percentile(List<Long> sorted, int pct) {
    int index = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
    return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
}
```

The analytics page at `GET /analytics` answers:

| Question | Source |
|---|---|
| Average cost per batch | `sum(aiEstimatedCostUsd) / count(batches)` |
| Cost per successful batch | `avg(aiEstimatedCostUsd) WHERE shortlistCount > 0` |
| Cost at projected traffic | avg × user-input volume (JS, client-side calculation) |
| Average latency | `avg(processingTimeMs)` |
| p95 / p99 latency | computed in Java from sorted `processingTimeMs` list |
| Which package is most expensive | `GROUP BY screeningPackage` with avg cost + latency |
| AI vs heuristic cost comparison | `GROUP BY scoringMode` with avg cost + latency |

Phase-level timing ("how much comes from model calls vs extraction") is logged per run but not yet stored in the database. Search application logs for `pipeline summary` to see per-phase breakdowns.

---

## 17. Report Generation

Reports are generated on demand (not during screening). They are triggered when the user clicks **View Report** on a results or history page.

**Batch report** (`GET /history/{batchId}/report`) — `ReportNarrativeService` receives the full batch summary and asks the AI to write an executive summary, methodology note, shortlist and rejection analysis, and suggested interview questions for the shortlist pool.

**Candidate report** (`GET /history/{batchId}/candidates/{rank}/report`) — `CandidateReportNarrativeService` generates a personalised assessment including: strengths summary, fit assessment paragraph, suggested interview focus areas, and tailored interview questions with follow-ups.

Both services have fallback implementations (`FallbackReportNarrativeService`, `FallbackCandidateReportNarrativeService`) that return template-based narratives when AI is disabled — the report pages always render.

Token usage from report generation is logged but not persisted to the database — it is separate from the screening batch cost.

---

## 18. In-Memory Stores

Two in-memory stores avoid the need for session state or a more complex state management mechanism. Both survive concurrent requests but not application restarts.

**Pattern used in both — bounded `LinkedHashMap` with LRU eviction:**

```java
private final Map<String, T> store = Collections.synchronizedMap(
    new LinkedHashMap<>(MAX_ENTRIES + 1, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, T> eldest) {
            return size() > MAX_ENTRIES;
        }
    });
```

`LinkedHashMap` with `accessOrder = false` evicts the oldest inserted entry when the cap is exceeded. `Collections.synchronizedMap()` makes it thread-safe.

**`RerunStore`** (max 10 entries) — stores job description text + CV file bytes (read eagerly from `MultipartFile` at save time) + screening package. Used to restore the form for a rerun without re-uploading.

**`JdReviewStore`** (max 20 entries) — stores the full `JdQualityAssessment` plus all form configuration needed to run screening if the user decides to proceed despite a weak JD. The `rerunId` field points to the corresponding `RerunStore` entry, which holds the CV file bytes.

Both stores use UUID string keys:

```java
String id = UUID.randomUUID().toString();
store.put(id, entry);
return id;
```

---

## 19. Configuration Reference

### Core Properties (`recruitment.*`)

| Property | Default | Description |
|---|---|---|
| `shortlist-count` | 3 | Default shortlist size |
| `max-job-description-words` | 1000 | Max word count for JD input |
| `analysis-cap` | 20 | Fallback cap when no `ScreeningDepth` UI value given |
| `upload-processing-cap` | 500 | Max CVs accepted per submission |
| `default-shortlist-quality` | `VERY_GOOD` | Default quality threshold (75) |
| `max-file-size-bytes` | 1048576 | Max size per uploaded file (1 MB) |
| `prefilter-borderline-margin` | 10.0 | Score margin for pre-filter rescue |
| `prefilter-max-rescue` | 8 | Max candidates rescued in pre-filter |
| `default-sector` | `generic` | Sector when recruiter leaves it blank |
| `jd-quality-check.enabled` | true | Toggle the JD quality gate on/off |

### Heuristic Scoring Weights (`recruitment.scoring.heuristic.*`)

Must sum to 90 or defaults are used:

| Property | Default |
|---|---|
| `essential-fit-max` | 40 |
| `broader-skill-fit-max` | 25 |
| `keyword-support-max` | 15 |
| `experience-fit-max` | 10 |
| `gap-penalty-severe` | −10 |
| `gap-penalty-moderate` | −5 |

### AI Scoring Weights (`recruitment.scoring.ai.*`)

Must sum to 1.0 (± 0.001) or defaults are used:

| Property | Default |
|---|---|
| `essential-fit-weight` | 0.35 |
| `experience-fit-weight` | 0.25 |
| `desirable-fit-weight` | 0.15 |
| `domain-fit-weight` | 0.15 |
| `credentials-fit-weight` | 0.10 |

**Per-sector AI weight overrides:**

```yaml
recruitment:
  scoring:
    ai:
      sector-overrides:
        healthcare:
          essential-fit-weight: 0.45
          credentials-fit-weight: 0.20
          experience-fit-weight: 0.15
          desirable-fit-weight: 0.10
          domain-fit-weight: 0.10
```

Only the specified weights are overridden; the rest inherit from the global values. Each override must still sum to 1.0.

### AI Cost (`recruitment.ai-cost.*`)

| Property | Default | Description |
|---|---|---|
| `prompt-price-per-million` | 0.15 | USD per 1M input tokens |
| `completion-price-per-million` | 0.60 | USD per 1M output tokens |

### Spring AI

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:disabled}   # Set to enable AI mode
      chat:
        options:
          model: gpt-4o-mini
    retry:
      max-attempts: 2
```

When `api-key` is `"disabled"`, all AI beans are absent and the application runs in heuristic-only mode.

### Skill Dictionary

```yaml
skill-dictionary:
  location: classpath:skills.yml  # accepts classpath or file-system paths
```

The YAML uses two top-level keys: `skills` (canonical skill display names) and `aliases` (lowercase alias → canonical name map). Missing or empty file → hardcoded defaults with a logged warning.

### Database Profiles

Default (in-memory, drops data on restart):

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:recruiter-dev;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
```

Persistent (activate with `SPRING_PROFILES_ACTIVE=persistent-h2`):

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/recruiter;MODE=PostgreSQL
```

`MODE=PostgreSQL` makes H2 behave like PostgreSQL — useful when planning to migrate to real Postgres later.

---

## 20. HTTP Endpoints and Pages

### HomeController

| Method | Path | Description |
|---|---|---|
| GET | `/` | Screening form; accepts `?rerun={id}` and `?jdOnly=true` |
| POST | `/analyse` | Synchronous screening (heuristic or AI). Renders `results.html`. |
| POST | `/analyse/stream` | SSE streaming submit. Fires progress events; emits `complete` on finish. |
| GET | `/rerun/{rerunId}` | JSON: `{ jobDescription, files[] (base64) }` for a previous run. 404 if not found. |
| GET | `/jd-review/{id}` | JD quality review page. |
| POST | `/analyse/proceed/{id}` | Proceed with screening despite weak JD. |

### HistoryController

| Method | Path | Description |
|---|---|---|
| GET | `/history` | All batches list, newest first. |
| GET | `/history/{batchId}` | Detail page for a specific batch. |
| GET | `/history/{batchId}/candidates/{rank}` | Candidate detail with scores and AI insights. |
| GET | `/history/{batchId}/eliminated` | Pre-filter eliminated candidates. |
| GET | `/history/{batchId}/report` | AI-generated batch narrative. |
| GET | `/history/{batchId}/candidates/{rank}/report` | AI-generated per-candidate narrative. |
| GET | `/loading` | Spinner; redirects to `?to=` param. |

### AnalyticsController

| Method | Path | Description |
|---|---|---|
| GET | `/analytics` | Cost and latency analytics dashboard. |

---

## 21. Error Handling and Degradation

The system degrades gracefully rather than failing hard in most cases:

| Scenario | Behaviour |
|---|---|
| No OpenAI API key | App starts, runs in heuristic-only mode |
| JD quality check AI call fails | Fail-open — screening proceeds as normal |
| AI job extraction fails | Entire batch falls back to heuristic mode |
| AI candidate evaluation fails for one candidate | Only that candidate falls back to heuristic; batch mode becomes `ai_with_fallbacks` |
| Corrupt or invalid PDF | Candidate retained with score 0.0 and failure summary; batch continues |
| Duplicate CV uploads | Removed before scoring; counts reported to recruiter |
| Missing skill dictionary file | Heuristic service falls back to hardcoded defaults with a logged warning |
| Missing sector-specific prompt | Falls back to `generic.txt` |
| Missing generic prompt | Fatal — startup or runtime load failure |
| AI unavailable for reports | Fallback implementations return template-based narratives |
| Multipart upload too large | `MultipartUploadExceptionHandler` returns 413 or redirects with `uploadError=max-size` |

`MultipartUploadExceptionHandler` runs at the highest precedence and handles multipart failures early. `GlobalExceptionHandler` handles unexpected exceptions from `HomeController` for both normal and SSE requests.

---

## 22. Test Coverage

Tests are under `src/test/java`. The test configuration (`application-test.yml`) uses in-memory H2 with `ddl-auto: create-drop` and disables AI by default.

Files present:

- `RecruiterApplicationTests` — context startup
- `HomeControllerTest` — controller/model behaviour
- `MultipartUploadExceptionHandlerTest`, `GlobalExceptionHandlerTest` — exception handling
- `AiAssessmentToCandidateEvaluationMapperTest` — AI score mapping
- `PdfDocumentExtractionServiceTest`, `CvTextExtractionServiceTest` — extraction
- `UploadedCvValidationServiceTest` — upload validation
- `ScreeningHistoryServiceTest` — history reconstruction
- `HeuristicJobDescriptionProfileFactoryTest`, `HeuristicCandidateProfileFactoryTest` — profile factories
- `TextProfileHeuristicsServiceTest` — skill/keyword/experience extraction
- `CandidateScoringServiceTest` — heuristic scoring
- `RankingServiceTest`, `ShortlistServiceTest` — ranking and shortlisting
- `CandidateScreeningFacadeTest` — batch orchestration
- `CvDeduplicationServiceTest` — deduplication
- `BatchMetricsFormatterTest` — formatting helpers

---

## 23. Extension Points

The codebase exposes natural seams for extension:

- **Add a new file format** — implement `DocumentExtractionService` with a `supports(MultipartFile)` method. Spring autowires it into `CvTextExtractionService`'s list automatically.
- **Add a new sector** — three files: add the enum value, write the prompt file, add heuristic skill terms. See Section 9 for the full steps.
- **Add richer AI audit data** — populate `aiCandidateProfileJson` and `aiFitAssessmentJson` in `ScreeningBatchPersistenceService`. The entity columns already exist.
- **Change the scoring model** — replace prompt content in `classpath:prompts/` without touching Java. Change AI dimension weights in `application.yml` without redeploying. Change heuristic weights the same way.
- **Swap the skill dictionary** — edit `skills.yml` with no code changes.
- **Upgrade to a more capable AI model** — configure `recruitment.ai-models.premium-pack.screening-model: gpt-4o` in `application.yml`.
- **Replace H2 with Postgres** — change the datasource URL and add the Postgres driver. The schema uses `MODE=PostgreSQL` on H2, so migration should be smooth.

---

## 24. Key Patterns Reference

### AI Response as Typed Record

```java
// 1. Define the record matching the expected JSON schema
record MyAiOutput(String field1, List<String> field2) {}

// 2. Prompt ends with "Return ONLY valid JSON"
private static final String PROMPT = """
    ... instructions ...
    Return ONLY valid JSON: { "field1": "...", "field2": [...] }
    """;

// 3. Call Spring AI with responseEntity()
AiResult<MyAiOutput> result = AiResponseSupport.toAiResult(
    chatClient.prompt()
        .system(PROMPT)
        .user(inputText)
        .call()
        .responseEntity(MyAiOutput.class)
);

// 4. Use the typed result and token usage
MyAiOutput output = result.result();
TokenUsage  usage  = result.tokenUsage();
```

### Optional AI with Heuristic Fallback

```java
private final Optional<SomeAiService> aiService;

public Result process(Input input) {
    if (aiService.isPresent()) return aiPath(input, aiService.get());
    return heuristicPath(input);
}
```

### Fail-Open AI Call

```java
private AssessmentResult assessSafely(String text) {
    try {
        return aiService.assess(text).result();
    } catch (Exception ex) {
        log.warn("AI assessment failed, proceeding: {}", ex.getMessage());
        return null;  // null = proceed without result
    }
}
```

### Conditional Bean with Fallback

```java
// AI implementation — only when API key is present
@Configuration
@ConditionalOnExpression("'${spring.ai.openai.api-key:disabled}' != 'disabled'")
class AiServiceConfiguration {
    @Bean SomeAiService someAiService(ChatClient.Builder builder) { ... }
}

// Fallback — only when the AI bean is absent
@Configuration
class FallbackConfiguration {
    @Bean
    @ConditionalOnMissingBean(SomeAiService.class)
    SomeAiService someAiServiceFallback() {
        return input -> new AiResult<>(defaultResult(), TokenUsage.ZERO);
    }
}
```

### SSE Progress from Virtual Thread

```java
// Controller — returns emitter immediately
@PostMapping("/long-task/stream")
public SseEmitter stream(Form form) {
    SseEmitter emitter = new SseEmitter(300_000L);
    Thread.startVirtualThread(() -> runTask(form, emitter));
    return emitter;
}

// Virtual thread — runs task and emits events
private void runTask(Form form, SseEmitter emitter) {
    try {
        facade.run(form, event -> sendEvent(emitter, "progress", event));
        sendEvent(emitter, "complete", Map.of("redirectUrl", "/results"));
        emitter.complete();
    } catch (Exception ex) {
        sendEvent(emitter, "error", Map.of("message", ex.getMessage()));
        emitter.complete();
    }
}
```

### `@EntityGraph` for Selective Eager Loading

```java
// Load one collection — avoids N+1
@EntityGraph(attributePaths = "children")
Optional<ParentEntity> findWithChildrenById(@Param("id") Long id);

// Load a different collection — separate method avoids Cartesian product
@EntityGraph(attributePaths = "otherChildren")
Optional<ParentEntity> findWithOtherChildrenById(@Param("id") Long id);
```

### Bounded In-Memory Store with UUID Keys

```java
@Component
class MyStore {
    private static final int MAX = 20;
    private final Map<String, MyEntry> store = Collections.synchronizedMap(
        new LinkedHashMap<>(MAX + 1, 0.75f, false) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, MyEntry> e) {
                return size() > MAX;
            }
        });

    String save(MyEntry entry) {
        String id = UUID.randomUUID().toString();
        store.put(id, entry);
        return id;
    }

    Optional<MyEntry> get(String id) {
        return Optional.ofNullable(store.get(id));
    }
}
```

### Safe Extraction from JPQL `Object[]`

```java
Object[] row = repo.findAggregateResult();
long tokens    = row[0] instanceof Number n    ? n.longValue()         : 0L;
BigDecimal cost = row[1] instanceof BigDecimal bd ? bd                 : BigDecimal.ZERO;
long count     = row[2] instanceof Number n    ? n.longValue()         : 0L;
```

---

## 25. Building a Similar Application — Tutorial

This section walks through how to build a new AI-backed Spring Boot application using the same patterns. The example is a document summarisation service, but the approach applies to any application that sends content to an AI and needs the result structured, persisted, and displayed.

### Step 1: Project Setup

```xml
<!-- pom.xml -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.0</version>
</parent>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>2.0.0-M4</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-thymeleaf</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-openai</artifactId>
    </dependency>
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

```yaml
# application.yml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:disabled}
  datasource:
    url: jdbc:h2:mem:myapp;DB_CLOSE_DELAY=-1
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
```

### Step 2: Define Your AI Output Record

Decide what you want the AI to return and write it as a Java record. The record must match the JSON schema you put in your system prompt.

```java
record DocumentSummary(
    String title,
    String oneLineSummary,
    List<String> keyPoints,
    String sentiment,          // "positive", "neutral", "negative"
    String suggestedAction,
    String confidence          // "HIGH", "MEDIUM", "LOW"
)
```

### Step 3: Write the System Prompt

Keep it in the service class as a constant. End with the exact JSON schema you expect.

```java
private static final String SYSTEM_PROMPT = """
    You are a document analyst. Analyse the document provided and return a structured summary.

    Rules:
    - Extract the main subject as title (max 10 words)
    - Summarise the document in one sentence
    - List 3-5 key points as short bullet phrases
    - Assess overall sentiment: positive, neutral, or negative
    - Suggest one concrete action the reader should take
    - Set confidence to HIGH, MEDIUM, or LOW based on document clarity

    Return ONLY valid JSON. No prose, no markdown fences.

    Schema:
    {
      "title": "string",
      "oneLineSummary": "string",
      "keyPoints": ["string"],
      "sentiment": "positive|neutral|negative",
      "suggestedAction": "string",
      "confidence": "HIGH|MEDIUM|LOW"
    }
    """;
```

### Step 4: Write the AI Service

```java
public interface DocumentAnalysisService {
    AiResult<DocumentSummary> analyse(String documentText);
}

@Service
public class SpringAiDocumentAnalysisService implements DocumentAnalysisService {

    private final ChatClient chatClient;

    public SpringAiDocumentAnalysisService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public AiResult<DocumentSummary> analyse(String documentText) {
        return AiResponseSupport.toAiResult(
            chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .options(OpenAiChatOptions.builder().model("gpt-4o-mini").build())
                .user(documentText)
                .call()
                .responseEntity(DocumentSummary.class)
        );
    }
}
```

Register it conditionally so the app works without an API key:

```java
@Configuration
@ConditionalOnExpression("'${spring.ai.openai.api-key:disabled}' != 'disabled'")
public class AiConfiguration {
    @Bean
    DocumentAnalysisService documentAnalysisService(ChatClient.Builder builder) {
        return new SpringAiDocumentAnalysisService(builder);
    }
}

@Configuration
public class FallbackConfiguration {
    @Bean
    @ConditionalOnMissingBean(DocumentAnalysisService.class)
    DocumentAnalysisService documentAnalysisServiceFallback() {
        return text -> new AiResult<>(
            new DocumentSummary("Unknown", "AI not configured", List.of(),
                "neutral", "Configure OpenAI API key", "LOW"),
            TokenUsage.ZERO
        );
    }
}
```

### Step 5: Write the JPA Entity

```java
@Entity
@Table(name = "document_analyses")
public class DocumentAnalysisEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "text", nullable = false)
    private String documentText;

    private String title;
    private String oneLineSummary;
    private String sentiment;
    private String suggestedAction;
    private String confidence;

    @Column(columnDefinition = "text")
    private String keyPointsJson;

    private Integer aiTotalTokens;

    @Column(precision = 8, scale = 4)
    private BigDecimal aiEstimatedCostUsd;

    private Long processingTimeMs;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() { this.createdAt = Instant.now(); }
}
```

### Step 6: Write the Service Layer

```java
@Service
@RequiredArgsConstructor
public class DocumentProcessingService {

    private final DocumentAnalysisService documentAnalysisService;
    private final DocumentAnalysisRepository repository;

    public DocumentAnalysisEntity process(String documentText) {
        long start = System.currentTimeMillis();

        AiResult<DocumentSummary> result;
        try {
            result = documentAnalysisService.analyse(documentText);
        } catch (Exception ex) {
            log.warn("AI analysis failed: {}", ex.getMessage());
            result = new AiResult<>(null, TokenUsage.ZERO);
        }

        DocumentSummary summary = result.result();
        long durationMs = System.currentTimeMillis() - start;

        DocumentAnalysisEntity entity = new DocumentAnalysisEntity();
        entity.setDocumentText(documentText);
        if (summary != null) {
            entity.setTitle(summary.title());
            entity.setOneLineSummary(summary.oneLineSummary());
            entity.setSentiment(summary.sentiment());
            entity.setKeyPointsJson(toJson(summary.keyPoints()));
        }
        entity.setAiTotalTokens(result.tokenUsage().totalTokens());
        entity.setProcessingTimeMs(durationMs);

        return repository.save(entity);
    }
}
```

### Step 7: Write the Controller and Template

```java
@Controller
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentProcessingService service;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("form", new DocumentForm());
        return "index";
    }

    @PostMapping("/analyse")
    public String analyse(@ModelAttribute DocumentForm form, Model model) {
        DocumentAnalysisEntity result = service.process(form.getText());
        model.addAttribute("result", result);
        return "result";
    }
}
```

```html
<!-- result.html -->
<h1 th:text="${result.title}">Title</h1>
<p th:text="${result.oneLineSummary}">Summary</p>
<ul>
    <li th:each="point : ${keyPoints}" th:text="${point}"></li>
</ul>
<span th:text="${'Confidence: ' + result.confidence}"></span>
<span th:text="${'Processing: ' + result.processingTimeMs + 'ms'}"></span>
```

### Step 8: Add SSE Streaming for Long Operations

```java
@PostMapping("/analyse/stream")
public SseEmitter analyseStream(@ModelAttribute DocumentForm form) {
    SseEmitter emitter = new SseEmitter(120_000L);
    Thread.startVirtualThread(() -> {
        try {
            emitter.send(SseEmitter.event().name("progress")
                .data("{\"message\": \"Analysing document...\"}"));

            DocumentAnalysisEntity result = service.process(form.getText());

            emitter.send(SseEmitter.event().name("complete")
                .data("{\"redirectUrl\": \"/result/" + result.getId() + "\"}"));
            emitter.complete();
        } catch (Exception ex) {
            try {
                emitter.send(SseEmitter.event().name("error")
                    .data("{\"message\": \"" + ex.getMessage() + "\"}"));
                emitter.complete();
            } catch (Exception ignored) {
                emitter.completeWithError(ex);
            }
        }
    });
    return emitter;
}
```

### Step 9: Test Without an API Key

Your application should start, accept submissions, and return the fallback response without an API key set. Every AI call must be optional from the user's perspective:

```bash
# Run without AI
mvn spring-boot:run
# Should start on port 8080, use fallback beans, not throw

# Run with AI
OPENAI_API_KEY=sk-... mvn spring-boot:run
# Should use real AI implementations
```

### Why This Application Chooses These Patterns

| Pattern | Simpler alternative | Why this app does it this way |
|---|---|---|
| Optional AI beans with fallback | Throw exception if key missing | Lets the app run offline; keeps AI testable in isolation |
| Domain records separate from JPA entities | JPA annotations on domain classes | Domain logic stays pure; entity changes don't force domain changes |
| `responseEntity(Class<T>)` for AI output | Parse JSON manually from string | Eliminates deserialization boilerplate; Jackson handles nulls and missing fields |
| Virtual threads for parallel AI calls | Fixed thread pool | No pool sizing, no starvation; handles variable workloads cheaply |
| Custom `Validator` class for form validation | All validation in annotations | Business rules (word count, file count) are not expressible as annotations |
| `@EntityGraph` for selective eager loading | `FetchType.EAGER` on collections | Avoids loading all children when only some are needed; prevents Cartesian joins |
| Bounded in-memory stores for rerun state | Database-backed session | Simple, no schema change, self-evicting; acceptable data loss on restart |
| Percentile computation in Java | SQL `PERCENTILE_CONT` | Avoids H2 compatibility uncertainty; equally correct on a sorted in-memory list |

---

*This document covers the application as it stood in April 2026. Update the relevant sections as features are added.*
