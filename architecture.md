# Architecture

This document describes the architecture of the Recruiter application: its structure, layers, request flows, and configuration reference.

---

## Technology Stack

| Concern | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0 (Spring MVC, Spring Data JPA) |
| Templating | Thymeleaf |
| AI integration | Spring AI (OpenAI / gpt-4o-mini) |
| PDF extraction | Apache PDFBox |
| Database | H2 (in-memory by default; file-based via `persistent-h2` profile) |
| Build | Maven |
| CSS | Bootstrap 5.3 |
| Concurrency | JDK virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`) |

---

## Application Structure

```
com.recruiter
├── config/          Configuration & properties
├── web/             HTTP layer: controllers, form DTOs, validators, exception handlers
├── domain/          Business domain models (records + enums)
├── service/         Business logic and orchestration
├── ai/              AI extraction, assessment, and prompt management
├── document/        PDF text extraction and CV validation
├── persistence/     JPA entities, repositories, and read-side services
├── report/          AI-generated narrative report services
└── support/         Utilities (metrics formatting)
```

---

## Package Reference

### `config/`

| Class | Purpose |
|---|---|
| `RecruitmentProperties` | Master config bean. All `recruitment.*` properties. Scoring weights, caps, thresholds, AI cost rates. |
| `AiServiceConfiguration` | Conditionally registers AI beans. Active only when `spring.ai.openai.api-key` ≠ `"disabled"`. |
| `ConcurrencyConfiguration` | Provides a virtual-thread executor (`screeningVirtualExecutor`) for parallel candidate scoring. |
| `MultipartConfig` | Sets `maxPartCount=150` on the Tomcat connector for multi-file uploads. |
| `SkillDictionaryProperties` | Loads the skill list and alias map from `classpath:skills.yml` (configurable). Falls back to hardcoded defaults if absent. |

### `web/`

| Class | Purpose |
|---|---|
| `HomeController` | Handles `GET /`, `POST /analyse`, `POST /analyse/stream`, and `GET /rerun/{id}`. |
| `HistoryController` | Serves history list, batch detail, candidate detail, eliminated list, and report pages. |
| `ScreeningForm` | Form DTO: jobDescription, cvFiles, shortlistCount, shortlistQuality, screeningDepth, scoringMode, sector. |
| `ScreeningFormValidator` | Custom validator: word count, shortlist count vs depth cap, file type/size/count. |
| `HomePageModelSupport` | Adds UI constants (sector list, file limits, word limit) to the model; creates a fresh form with config defaults. |
| `RerunStore` | In-memory LRU cache (max 10). Stores job description text + file bytes after each screening to enable rerun. |
| `GlobalExceptionHandler` | Catches unhandled exceptions from `HomeController`; redirects or returns JSON errors for SSE clients. |
| `MultipartUploadExceptionHandler` | Highest-precedence handler for multipart/size exceptions; returns 413 or redirects with `uploadError=max-size`. |

### `domain/`

All classes are immutable records or enums. No persistence annotations.

| Class | Purpose |
|---|---|
| `ScoringMode` | Enum: `heuristic`, `ai`, `ai_with_fallbacks` |
| `ShortlistQuality` | Enum with score thresholds: EXCELLENT (90), VERY_GOOD (75), GOOD (60), ALL (40) |
| `ScreeningDepth` | Enum controlling analysis cap: FAST (10), BALANCED (20), THOROUGH (50) |
| `CandidateProfile` | Candidate data extracted from a CV: name, filename, text, skills, years of experience |
| `JobDescriptionProfile` | Job data extracted from description: text, skills, required keywords, years |
| `CandidateEvaluation` | Full scoring result for one candidate: score, breakdown, summary, AI fields, shortlist flag |
| `CandidateScoreBreakdown` | Score components: skillScore, keywordScore, experienceScore |
| `ScreeningResult` | Job profile + list of all candidate evaluations for one run |
| `ScreeningRunResult` | Full pipeline result: batch ID, run metrics, token usage, cost, `ScreeningResult` |

### `service/`

| Class | Purpose |
|---|---|
| `CandidateScreeningFacade` | Main pipeline orchestrator. Coordinates all phases from extraction to persistence. |
| `CandidateScoringService` | Heuristic 0-100 scoring. Five components: essential fit, broader skill fit, keyword support, experience fit, gap penalty. |
| `RankingService` | Sorts `CandidateEvaluation` list by score descending, name ascending. |
| `ShortlistService` | Marks top-N candidates with score ≥ threshold as shortlisted. Resolves defaults from config when inputs are null. |
| `CvDeduplicationService` | Two-pass dedup: exact (MD5 hash) and near-duplicate (Jaccard similarity ≥ 0.85, same name). |
| `TextProfileHeuristicsService` | Skill extraction with alias resolution; keyword extraction; requirement classification (essential/desirable); experience regex. |
| `HeuristicCandidateProfileFactory` | Builds `CandidateProfile` from CV text. |
| `HeuristicJobDescriptionProfileFactory` | Builds `JobDescriptionProfile` from JD text. |
| `PipelineTimer` | Records duration per named phase; returns total elapsed time. |
| `ScreeningProgressEvent` | Immutable event record: phase, completed, total, message, candidateName. |
| `ScreeningProgressListener` | Functional interface for SSE progress callbacks. |

### `ai/`

| Class | Purpose |
|---|---|
| `JobDescriptionAiExtractor` / `SpringAiJobDescriptionAiExtractor` | Extracts structured `AiJobDescriptionProfile` from JD text via LLM. |
| `CandidateAiExtractor` / `SpringAiCandidateAiExtractor` | Extracts structured `AiCandidateProfile` from CV text via LLM. |
| `AiSkillExtractor` / `SpringAiSkillExtractor` | Extracts must-have and nice-to-have skill lists from JD for pre-filter boosting. |
| `FitAssessmentAiService` / `SpringAiFitAssessmentAiService` | Compares AI job + candidate profiles; returns `AiFitAssessment` with five dimension judgements and recruiter explanation. |
| `AiAssessmentToCandidateEvaluationMapper` | Converts `AiFitAssessment` dimension judgements to a 0-100 score with sector-specific weights and confidence modifier. |
| `PromptProviderFactory` / `PromptLoaderService` | Loads sector-specific system prompts from `classpath:prompts/`. Cached after first load. Falls back to `generic.txt`. |
| `Sector` | Enum: 11 sectors. Used for skill dictionary selection, system prompt selection, and AI weight overrides. |
| `SectorSkillDictionary` | Static map of sector → skill term list. Injected into heuristic extraction for both JD and CVs. |
| `AiResult<T>` | Wraps any AI response with associated `TokenUsage`. |
| `TokenUsage` / `TokenUsageAccumulator` | Track prompt/completion tokens; compute estimated cost. |
| `AiPromptVersions` | String constants for prompt version IDs recorded in each batch. |

### `document/`

| Class | Purpose |
|---|---|
| `PdfDocumentExtractionService` | Extracts text from PDF files using Apache PDFBox. |
| `CvTextExtractionService` | Iterates uploaded files; delegates extraction; collects success/failure outcomes. |
| `UploadedCvValidationService` | Pre-extraction validation: count, extension, content-type, and size per file. |
| `DocumentExtractionOutcome` | Success/failure wrapper: extracted document or failure message. |
| `ExtractedDocument` | Record: original filename + extracted text. |

### `persistence/`

| Class | Purpose |
|---|---|
| `ScreeningBatchEntity` | JPA entity: batch metadata, AI usage metrics, one-to-many to evaluations and eliminated candidates. |
| `CandidateEvaluationEntity` | JPA entity: per-candidate score, breakdown, AI profile JSON, rank position. |
| `EliminatedCandidateEntity` | JPA entity: candidates removed in pre-filtering with pre-filter score and reason. |
| `ScreeningBatchRepository` | JPA repository. Custom queries: ordered list, detail fetch with JOIN FETCH, AI usage aggregate, processing time update. |
| `ScreeningBatchPersistenceService` | Assembles and saves a full batch with all child entities in one transaction. |
| `ScreeningHistoryService` | Read-side service: transforms entities to display records, computes aggregate stats. |
| `StoredScreeningBatchResult` | Display record for a single batch (used on the results/detail page). |
| `StoredCandidateDetail` | Display record for a single candidate (used on the candidate detail page). |
| `StoredEliminatedCandidate` | Display record for a pre-filter eliminated candidate. |
| `ScreeningBatchHistoryItem` | Compact summary record for the history list. |
| `AiUsageSummary` | Aggregate: total tokens, total cost, batch count. |
| `EliminatedCandidateSnapshot` | In-pipeline record capturing who was eliminated and why, before persistence. |

### `report/`

| Class | Purpose |
|---|---|
| `ReportNarrativeService` / `SpringAiReportNarrativeService` | Generates a batch-level executive summary and interview questions via AI. |
| `CandidateReportNarrativeService` / `SpringAiCandidateReportNarrativeService` | Generates a per-candidate narrative report via AI. |
| `FallbackReportNarrativeService` / `FallbackCandidateReportNarrativeService` | Template-based fallbacks when AI is unavailable. |
| `ReportNarrative` | Record: executiveSummary + list of `InterviewQuestion`. |
| `CandidateReportNarrative` | Record: narrative text + list of `InterviewQuestion`. |
| `InterviewQuestion` | Record: question text + rationale. |

---

## HTTP Endpoints

### HomeController

| Method | Path | Description |
|---|---|---|
| GET | `/` | Screening form. Accepts `rerun` param to pre-populate from cache. |
| GET | `/rerun/{rerunId}` | JSON: job description text + base64-encoded CV files for a previous run. |
| POST | `/analyse` | Traditional form submit. Runs full pipeline, renders `results.html`. |
| POST | `/analyse/stream` | SSE streaming submit. Fires progress events; emits `complete` with redirect URL on finish. |

### HistoryController

| Method | Path | Description |
|---|---|---|
| GET | `/loading` | Loading spinner page. Param: `to` (redirect target). |
| GET | `/history` | All batches list, newest first, with aggregate AI usage. |
| GET | `/history/{batchId}` | Detail page for a specific batch (same template as results). |
| GET | `/history/{batchId}/candidates/{rankPosition}` | Individual candidate detail with scores, AI insights. |
| GET | `/history/{batchId}/eliminated` | Candidates dropped in the pre-filtering phase. |
| GET | `/history/{batchId}/report` | AI-generated batch narrative (executive summary + interview questions). |
| GET | `/history/{batchId}/candidates/{rankPosition}/report` | AI-generated per-candidate narrative. |

---

## Thymeleaf Templates

| Template | Serves | Key content |
|---|---|---|
| `index.html` | `GET /` | Screening form with all fields; real-time word counter; SSE progress overlay |
| `results.html` | `POST /analyse`, `GET /history/{id}` | Run summary, shortlist table, metrics, rerun and report links |
| `candidate-detail.html` | `GET /history/{id}/candidates/{rank}` | Scores, breakdown, AI strengths/gaps/probe areas |
| `candidate-report.html` | `GET /history/{id}/candidates/{rank}/report` | AI narrative + interview questions, print-ready |
| `report.html` | `GET /history/{id}/report` | Batch executive summary, all candidate tables, interview questions, print-ready |
| `eliminated.html` | `GET /history/{id}/eliminated` | Pre-filter eliminated candidates with scores and reason |
| `history.html` | `GET /history` | Batch list table with AI cost and processing time |
| `loading.html` | `GET /loading` | Spinner; redirects to `to` param |

---

## Request Flows

### Synchronous Screening (`POST /analyse`)

```
Browser POST /analyse
  → ScreeningFormValidator (word count, file count/type/size, shortlist ≤ depth cap)
  → HomeController.analyse()
  → CandidateScreeningFacade.screen(jd, shortlistCount, minScore, mode, files, sector, analysisCap)
      → PDF text extraction  (CvTextExtractionService)
      → Deduplication        (CvDeduplicationService)
      → AI prep if needed    (JobDescriptionAiExtractor, AiSkillExtractor)
      → Pre-filter if needed (heuristic scoring, margin-based rescue)
      → Full scoring         (heuristic: sequential | AI: parallel virtual threads)
      → Ranking              (RankingService)
      → Shortlisting         (ShortlistService)
      → Persistence          (ScreeningBatchPersistenceService)
      → return ScreeningRunResult
  → Render results.html
```

### Streaming Screening (`POST /analyse/stream`)

```
Browser POST /analyse/stream
  → validation (same)
  → HomeController.analyseWithProgress()
      → Detach files to temp storage
      → Start virtual thread
          → SSE: progress events per phase/candidate
          → CandidateScreeningFacade.screen(... progressListener, ... analysisCap)
          → SSE: complete event { redirectUrl: "/history/{batchId}" }
      → Return SseEmitter immediately
  → JavaScript receives events → updates progress bar
  → On "complete" event: window.location.href = redirectUrl
```

### History & Reports

```
GET /history
  → ScreeningHistoryService.listHistory() → list of ScreeningBatchHistoryItem
  → Render history.html

GET /history/{batchId}/report
  → ScreeningHistoryService.findBatch(batchId)
  → ScreeningHistoryService.findEliminatedCandidates(batchId)
  → ReportNarrativeService.generate(ReportNarrativeRequest)
  → Render report.html
```

---

## Analysis Cap Resolution

The `analysisCap` controls how many candidates proceed to full scoring. It is resolved at the start of every screening run in this order:

1. **`ScreeningDepth` from the UI form** — the user selects FAST (10), BALANCED (20), or THOROUGH (50). This value is passed as `overrideAnalysisCap` to `CandidateScreeningFacade`.
2. **`recruitment.analysis-cap` config property** — used as fallback when `overrideAnalysisCap` is null (e.g. programmatic calls).

```
effectiveAnalysisCap = overrideAnalysisCap != null
                         ? overrideAnalysisCap
                         : properties.getAnalysisCap()
```

The validator also ensures `shortlistCount ≤ effectiveAnalysisCap` at form submission time, so the shortlist cannot exceed the candidates actually scored.

---

## Database Schema (auto-created by Hibernate)

### `screening_batch`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | Auto-generated |
| `created_at` | TIMESTAMP | Set on `@PrePersist` |
| `job_description_text` | TEXT | Original user input |
| `shortlist_count` | INT | |
| `scoring_mode` | VARCHAR | `heuristic`, `ai`, `ai_with_fallbacks` |
| `sector` | VARCHAR | Sector enum name |
| `total_cvs_received` | INT | |
| `candidates_scored` | INT | |
| `shortlist_threshold` | DECIMAL | |
| `ai_job_description_profile_json` | TEXT | Serialized `AiJobDescriptionProfile` (nullable) |
| `prompt_versions` | VARCHAR | Comma-separated prompt IDs |
| `ai_prompt_tokens` | INT | nullable |
| `ai_completion_tokens` | INT | nullable |
| `ai_total_tokens` | INT | nullable |
| `ai_estimated_cost_usd` | DECIMAL | nullable |
| `processing_time_ms` | BIGINT | nullable; updated after save |

### `candidate_evaluation`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `screening_batch_id` | BIGINT FK | → `screening_batch.id` |
| `candidate_name` | VARCHAR | |
| `candidate_filename` | VARCHAR | |
| `extracted_skills` | TEXT | |
| `years_of_experience` | INT | nullable |
| `score` | DECIMAL(5,1) | |
| `skill_score` | DECIMAL | |
| `keyword_score` | DECIMAL | |
| `experience_score` | DECIMAL | |
| `summary` | TEXT | |
| `scoring_path` | VARCHAR | `heuristic`, `ai` |
| `ai_candidate_profile_json` | TEXT | nullable |
| `ai_fit_assessment_json` | TEXT | nullable |
| `rank_position` | INT | 1-based |
| `shortlisted` | BOOLEAN | |

### `eliminated_candidate`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `screening_batch_id` | BIGINT FK | |
| `candidate_name` | VARCHAR | |
| `candidate_filename` | VARCHAR | |
| `score` | DECIMAL | Pre-filter score |
| `reason` | VARCHAR | Elimination reason |

---

## Configuration Reference

All properties are under the `recruitment` prefix in `application.yml`.

### Core settings

| Property | Default | Description |
|---|---|---|
| `shortlist-count` | `3` | Default shortlist size when none provided |
| `max-job-description-words` | `1000` | Maximum words allowed in the job description |
| `analysis-cap` | `20` | Fallback analysis cap (used when no `ScreeningDepth` override given) |
| `upload-processing-cap` | `500` | Maximum CVs accepted per submission |
| `default-shortlist-quality` | `VERY_GOOD` | Default score threshold (75) |
| `max-file-size-bytes` | `1048576` (1 MB) | Maximum size per uploaded PDF |
| `prefilter-borderline-margin` | `10.0` | Score margin for pre-filter borderline rescue |
| `prefilter-max-rescue` | `8` | Maximum candidates rescued in pre-filter |
| `default-sector` | `generic` | Sector used when recruiter leaves it blank |

### Heuristic scoring weights (`recruitment.scoring.heuristic.*`)

Weights must sum to 90 or defaults are used.

| Property | Default |
|---|---|
| `essential-fit-max` | `40` |
| `broader-skill-fit-max` | `25` |
| `keyword-support-max` | `15` |
| `experience-fit-max` | `10` |
| `gap-penalty-severe` | `-10` |
| `gap-penalty-moderate` | `-5` |

### AI scoring weights (`recruitment.scoring.ai.*`)

Weights must sum to 1.0 (± 0.001) or defaults are used.

| Property | Default |
|---|---|
| `essential-fit-weight` | `0.35` |
| `experience-fit-weight` | `0.25` |
| `desirable-fit-weight` | `0.15` |
| `domain-fit-weight` | `0.15` |
| `credentials-fit-weight` | `0.10` |

Per-sector overrides: `recruitment.scoring.ai.sector-overrides.<sector>.<weight-key>`. Invalid merged overrides revert to defaults.

### AI cost (`recruitment.ai-cost.*`)

| Property | Default | Description |
|---|---|---|
| `prompt-price-per-million` | `0.15` | USD per 1M input tokens |
| `completion-price-per-million` | `0.60` | USD per 1M output tokens |

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

---

## Concurrency Model

- **Synchronous requests** (`POST /analyse`): Blocking call on the HTTP thread.
- **Streaming requests** (`POST /analyse/stream`): A virtual thread is started for the screening pipeline; the HTTP thread returns the `SseEmitter` immediately. The virtual thread emits SSE events.
- **AI candidate scoring**: Each candidate is scored in a `CompletableFuture` submitted to `screeningVirtualExecutor` (virtual-thread-per-task), so all candidates run concurrently during AI mode.
- **Heuristic candidate scoring**: Sequential loop (fast enough that concurrency is not needed).

---

## AI Availability & Fallback Strategy

| Scenario | Behaviour |
|---|---|
| `api-key=disabled` | All AI beans absent; form defaults to heuristic; AI mode silently falls back to heuristic |
| AI job extraction fails | Pre-filter uses heuristic skill list only; mode stays `ai` if at least one candidate scores via AI |
| AI candidate extraction/assessment fails for one candidate | That candidate scored heuristically; mode becomes `ai_with_fallbacks` |
| AI unavailable for reports | `FallbackReportNarrativeService` / `FallbackCandidateReportNarrativeService` generate template-based narratives |

---

## Feature History (recent, newest first)

| Feature | What it introduced |
|---|---|
| Screening Depth UI | `ScreeningDepth` enum, `screeningDepth` field on `ScreeningForm`, analysis cap threaded through facade as an override, validator bounds `shortlistCount` against selected depth |
| Reports & Narratives | Batch and candidate AI narrative reports, print-ready templates, `ReportNarrativeService`, `CandidateReportNarrativeService`, fallback implementations |
| Repeat Screening (Rerun) | `RerunStore`, `GET /rerun/{id}` endpoint, JS-side form restoration from base64 file data |
| Screening History | `HistoryController`, `ScreeningHistoryService`, `history.html`, eliminated candidates page |
| Sector-Aware Scoring | `Sector` enum (11 sectors), `SectorSkillDictionary`, sector-specific system prompts, per-sector AI weight overrides |
| Pre-filter & Deduplication | `CvDeduplicationService`, margin-based rescue pre-filter, `EliminatedCandidateEntity` |
| SSE Progress Streaming | `POST /analyse/stream`, virtual thread pipeline, `ScreeningProgressListener`, progress overlay in `index.html` |
| AI Scoring (Spring AI) | `AiServiceConfiguration`, all Spring AI extractors and assessment services, `AiAssessmentToCandidateEvaluationMapper` |
| Heuristic Scoring | `CandidateScoringService`, `TextProfileHeuristicsService`, skill aliases, requirement classification |