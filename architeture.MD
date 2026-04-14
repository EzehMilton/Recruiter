# Recruiter Application Architecture

## 1. Purpose

Recruiter is a Spring Boot web application for batch CV screening. A recruiter pastes a job description, uploads one or more candidate CVs as PDF files, chooses a shortlist size, selects a quality threshold, optionally selects an industry sector, and then runs either:

- `heuristic` screening: fully local, deterministic scoring
- `ai` screening: OpenAI-backed structured extraction and fit assessment, with heuristic fallback when AI is unavailable or an individual AI candidate evaluation fails

The application produces:

- a ranked list of candidate evaluations
- shortlist flags based on rank and threshold
- screening history persisted to H2
- candidate detail views
- first-pass eliminated candidate views when a large batch is reduced before full analysis
- AI usage and estimated cost metadata when AI is used

This file documents the implemented design, runtime flow, components, data model, persistence model, UI behavior, configuration, and extension points.

## 2. Technology Stack

From [pom.xml](/Users/miezeh/Desktop/Recruiter/pom.xml):

- Framework: Spring Boot `4.0.0`
- Language: Java `21`
- Web MVC: `spring-boot-starter-web`
- Templates: `spring-boot-starter-thymeleaf`
- Validation: `spring-boot-starter-validation`
- Persistence: `spring-boot-starter-data-jpa`
- Database: H2
- PDF parsing: Apache PDFBox `3.0.7`
- AI integration: Spring AI `2.0.0-M4` with OpenAI starter
- Build tool: Maven via `mvnw`
- Test stack: Spring Boot Test, JUnit 5, AssertJ

## 3. Repository Shape

Top-level structure:

- [pom.xml](/Users/miezeh/Desktop/Recruiter/pom.xml): build and dependencies
- [src/main/java/com/recruiter](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter): application code
- [src/main/resources/application.yml](/Users/miezeh/Desktop/Recruiter/src/main/resources/application.yml): runtime configuration
- [src/main/resources/templates](/Users/miezeh/Desktop/Recruiter/src/main/resources/templates): Thymeleaf views
- [src/main/resources/prompts](/Users/miezeh/Desktop/Recruiter/src/main/resources/prompts): sector-specific AI system prompts
- [src/main/resources/skills.yml](/Users/miezeh/Desktop/Recruiter/src/main/resources/skills.yml): external skill dictionary
- [src/test/java](/Users/miezeh/Desktop/Recruiter/src/test/java): unit and slice tests
- [data](/Users/miezeh/Desktop/Recruiter/data): optional persistent H2 files when `persistent-h2` profile is used

Main package layout:

- `com.recruiter.web`: MVC controllers, form model, validation, exception handling
- `com.recruiter.service`: orchestration and heuristic business logic
- `com.recruiter.ai`: AI contracts, data shapes, prompt loading, Spring AI implementations, mapper
- `com.recruiter.document`: upload validation and PDF text extraction
- `com.recruiter.domain`: immutable domain records returned to the web layer
- `com.recruiter.persistence`: JPA entities, repositories, persistence and history read models
- `com.recruiter.config`: typed configuration and infrastructure beans
- `com.recruiter.support`: display-formatting helpers

## 4. Runtime Architecture

At a high level the application is a classic layered Spring MVC application with one dominant orchestrator service.

Runtime flow:

1. Browser submits a screening form.
2. `HomeController` validates input and delegates to `CandidateScreeningFacade`.
3. `CandidateScreeningFacade` coordinates extraction, deduplication, optional reduction, scoring, ranking, shortlisting, and persistence.
4. The persisted screening batch is later read back through `HistoryController` and `ScreeningHistoryService`.
5. Thymeleaf templates render HTML pages for the form, results, history, candidate detail, and eliminated candidates.

Primary design choice:

- The application centralizes orchestration in `CandidateScreeningFacade` rather than distributing batch flow across many services. Supporting services stay fairly narrow: extraction, heuristics, scoring, persistence, and formatting.

## 5. Entry Point and Bootstrapping

Application entry point:

- [RecruiterApplication.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/RecruiterApplication.java)

Important startup behavior:

- `@SpringBootApplication` enables component scanning and auto-configuration.
- `@ConfigurationPropertiesScan` enables typed properties binding, notably `RecruitmentProperties`.
- AI beans are conditionally registered only when a real API key is provided.

AI activation behavior:

- [AiServiceConfiguration.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/AiServiceConfiguration.java) is guarded by:
  - `@ConditionalOnExpression("'${spring.ai.openai.api-key:disabled}' != 'disabled'")`
- If `OPENAI_API_KEY` is absent, the app still starts and only heuristic mode is operational.

## 6. Request/Response Model

### Synchronous request path

Used when scoring mode is not AI.

- Browser posts form to `POST /analyse`
- Controller validates
- Controller runs full screening synchronously
- Results page is rendered directly

### Streaming request path

Used by the frontend only when AI mode is selected.

- Browser intercepts the normal form submit in [index.html](/Users/miezeh/Desktop/Recruiter/src/main/resources/templates/index.html)
- It submits `fetch('/analyse/stream', { Accept: 'text/event-stream' })`
- `HomeController.analyseWithProgress()` returns an `SseEmitter`
- Background work runs on a virtual thread started by `Thread.startVirtualThread(...)`
- Progress events are emitted as SSE blocks
- On completion the client redirects to `/history/{batchId}`

This gives the application two distinct user experiences without creating separate APIs.

## 7. Web Layer

### 7.1 `HomeController`

Source:

- [HomeController.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/web/HomeController.java)

Responsibilities:

- Render the landing page at `GET /`
- Accept form submissions at `POST /analyse`
- Accept streaming submissions at `POST /analyse/stream`
- Serve rerun snapshot data at `GET /rerun/{rerunId}`
- Bind and validate `ScreeningForm`
- Translate domain results into model attributes consumed by `results.html`
- Manage detached temporary upload files for the streaming path
- Emit structured SSE progress and error events

Important behaviors:

- Uses `@InitBinder("screeningForm")` to attach `ScreeningFormValidator`
- Builds a success message that differs if pre-filter reduction occurred
- In streaming mode it clones uploaded multipart files into temp files because the request thread will end while background analysis continues
- Cleans up detached files in a `finally` block
- Before running either screening path, calls `RerunStore.save()` to snapshot the job description and file bytes. For the synchronous path the snapshot is saved while multipart files are still live on the request. For the streaming path the snapshot is saved at the top of `runStreamingScreening()` before `cleanupDetachedFiles()` can execute
- For the synchronous path the resulting `rerunId` is added to the Thymeleaf model so `results.html` can embed it in the Rerun Screening button
- For the streaming path the `rerunId` is included in the SSE `complete` event payload so the browser can store it in `sessionStorage` before redirecting
- `GET /` accepts an optional `?rerun=` parameter; if present the `rerunId` is forwarded to `index.html` for client-side pre-population
- `GET /rerun/{rerunId}` is a `@ResponseBody` endpoint returning JSON with `jobDescription` and a `files` array (each file serialised as `filename`, `contentType`, and Base64-encoded `data`). Returns 404 if the ID is not in `RerunStore`

### 7.2 `RerunStore`

Source:

- [RerunStore.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/web/RerunStore.java)

Package-private Spring `@Component` in `com.recruiter.web`.

Purpose: hold recent screening snapshots in memory so a recruiter can return to the form with the same job description and CV files already loaded, without having to re-paste or re-upload anything.

Behavior:

- `save(jobDescription, files)` reads all multipart file bytes immediately, wraps them in `RerunFile` records, and stores the snapshot under a new UUID. Callers are free to release or clean up the multipart files after this call returns
- `get(rerunId)` returns `Optional<RerunSnapshot>` — empty if the ID is unknown or has been evicted
- Backed by a `Collections.synchronizedMap(new LinkedHashMap(...))` capped at `MAX_ENTRIES = 10`; once the cap is reached the oldest entry is evicted automatically
- Entries do not survive an application restart

Inner records:

- `RerunSnapshot(jobDescription, files)` — the full snapshot
- `RerunFile(filename, contentType, bytes)` — one uploaded file's data

### 7.4 `HistoryController`

Source:

- [HistoryController.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/web/HistoryController.java)

Routes:

- `GET /history`: list historical screening batches
- `GET /history/{batchId}`: show a stored results page
- `GET /history/{batchId}/candidates/{rankPosition}`: show candidate detail
- `GET /history/{batchId}/eliminated`: show candidates eliminated during first-pass reduction

It does not recompute results. It reads persisted state via `ScreeningHistoryService`.

### 7.5 `ScreeningForm`

Source:

- [ScreeningForm.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/web/ScreeningForm.java)

Fields:

- `jobDescription`
- `shortlistCount`
- `shortlistQuality`
- `scoringMode`
- `sector`
- `cvFiles`

Validation annotations:

- `@NotBlank` on `jobDescription`
- `@NotNull` and `@Min(1)` on `shortlistCount`
- `@NotNull` on `shortlistQuality`
- `@NotBlank` on `scoringMode`

### 7.6 `ScreeningFormValidator`

Source:

- [ScreeningFormValidator.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/web/ScreeningFormValidator.java)

Additional validation beyond bean annotations:

- Job description word count must not exceed `recruitment.max-job-description-words`
- Shortlist count must not exceed `recruitment.analysis-cap`
- Uploaded CVs are validated by `UploadedCvValidationService`

### 7.7 `HomePageModelSupport`

Source:

- [HomePageModelSupport.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/web/HomePageModelSupport.java)

Responsibilities:

- Create default `ScreeningForm` instances
- Add shared model constants:
  - max candidates
  - max words
  - max file size label
  - sector list
- Generate user-facing upload error messages

### 7.8 Exception handling

Sources:

- [MultipartUploadExceptionHandler.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/web/MultipartUploadExceptionHandler.java)
- [GlobalExceptionHandler.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/web/GlobalExceptionHandler.java)

Design:

- `MultipartUploadExceptionHandler` runs at highest precedence and handles multipart failures early
- For normal requests it redirects back to `/` with a query parameter
- For streaming requests it returns JSON error payloads with appropriate status codes
- `GlobalExceptionHandler` handles unexpected exceptions for `HomeController`

This separation gives clearer behavior for upload-specific failures versus general processing failures.

## 8. Frontend / View Architecture

Templates:

- [index.html](/Users/miezeh/Desktop/Recruiter/src/main/resources/templates/index.html)
- [results.html](/Users/miezeh/Desktop/Recruiter/src/main/resources/templates/results.html)
- [history.html](/Users/miezeh/Desktop/Recruiter/src/main/resources/templates/history.html)
- [candidate-detail.html](/Users/miezeh/Desktop/Recruiter/src/main/resources/templates/candidate-detail.html)
- [eliminated.html](/Users/miezeh/Desktop/Recruiter/src/main/resources/templates/eliminated.html)

### 8.1 Landing page

`index.html` provides:

- job description textarea with live word count
- shortlist size input
- shortlist quality dropdown
- scoring mode radio buttons
- optional sector dropdown
- PDF multi-file upload
- progress overlay for AI mode

Frontend behavior:

- If mode is `heuristic`, form submits normally
- If mode is `ai`, JavaScript replaces form submit with a streaming `fetch`
- Progress phases are visualized using:
  - phase label
  - progress message
  - progress count
  - last completed candidate name
  - a coarse progress bar

The frontend maps server phases to labels:

- `extracting`
- `prefiltering`
- `scoring`
- `finalising`
- `complete`

Rerun pre-population behavior:

- When the page loads with a `?rerun=` query parameter, a hidden `<span id="rerunMeta">` carries the `rerunId` as a `data-rerun-id` attribute
- JavaScript fetches `GET /rerun/{rerunId}` and receives `{ jobDescription, files[] }`
- The job description is written directly into the textarea (word count updates automatically)
- Each file is decoded from Base64 and added to a `DataTransfer` object; the resulting `FileList` is assigned to the file input using the `DataTransfer` API
- A dismissable info banner confirms how many CVs were pre-loaded
- The SSE `complete` event handler stores the `rerunId` in `sessionStorage` (keyed by `batchId`) before redirecting, so the results page can wire the Rerun button even though it is rendered server-side by `HistoryController` without access to the rerun ID

### 8.2 Results page

`results.html` shows:

- batch metadata
- scoring mode
- AI usage display
- reduction summary if large-batch prefiltering occurred
- duplicate-removal summary (exact duplicates and near-duplicates reported separately)
- shortlist threshold information
- ranked candidate cards

Each candidate card shows:

- rank
- candidate name
- scoring-path badge
- match label derived from score band
- shortlist state
- summary

Action buttons:

- **Rerun Screening** — appears when a `rerunId` is available. For the heuristic path, the `rerunId` is server-rendered into the button's `data-rerun-id` attribute. For the AI path, it is read from `sessionStorage` (keyed as `recruiter.rerunId.{batchId}`). In both cases the button href is set to `/?rerun={rerunId}`. The `sessionStorage` entry is intentionally never removed on this page so that navigating to a candidate detail and clicking Back to Results re-shows the button correctly
- **Run Another Screening** — navigates to `/` with no pre-population
- **View History** — navigates to the history list
- **View Eliminated Candidates** — shown when `batchId` is available

### 8.3 Candidate detail page

`candidate-detail.html` renders different sections based on whether the candidate was AI-scored or heuristic-scored.

AI detail rendering:

- total score bar
- AI confidence
- top strengths
- top gaps
- interview probe areas
- recruiter-facing explanation
- safe extracted indicators such as filename, estimated experience, and extracted skills

Heuristic detail rendering:

- weighted score breakdown
- extracted skills
- estimated experience
- summary

### 8.4 History page

`history.html` lists historical batches with:

- batch ID
- created date
- scoring mode
- AI usage summary
- CV received versus CV analyzed
- duration
- shortlist count

### 8.5 Eliminated candidates page

`eliminated.html` shows candidates removed during first-pass pre-filtering. It does not show final scores because these candidates were never fully analyzed.

## 9. Core Orchestration Layer

### 9.1 `CandidateScreeningFacade`

Source:

- [CandidateScreeningFacade.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/service/CandidateScreeningFacade.java)

This is the central orchestration service. It coordinates all major pipeline phases.

Injected collaborators:

- `CvTextExtractionService`
- `JobDescriptionProfileFactory`
- `CandidateProfileFactory`
- `CandidateScoringService`
- `TextProfileHeuristicsService`
- `CvDeduplicationService`
- `RankingService`
- `ShortlistService`
- `ScreeningBatchPersistenceService`
- `RecruitmentProperties`
- optional AI services:
  - `JobDescriptionAiExtractor`
  - `AiSkillExtractor`
  - `CandidateAiExtractor`
  - `FitAssessmentAiService`
- `AiAssessmentToCandidateEvaluationMapper`
- `PromptProviderFactory`
- `ExecutorService screeningVirtualExecutor`

Pipeline phases tracked with `PipelineTimer`:

- `resolve_settings`
- `job_profile_extraction`
- `cv_text_extraction`
- `deduplication`
- `ai_job_extraction`
- `pre_filter`
- `candidate_scoring`
- `ranking_and_shortlisting`
- `persistence`

High-level screen algorithm:

1. Resolve shortlist count, threshold, scoring mode, and sector.
2. Build heuristic job profile.
3. Enrich job profile with sector-specific heuristic terms.
4. Extract text from uploaded CV PDFs.
5. Deduplicate exact and near duplicates.
6. If AI mode, attempt AI job extraction and optional AI skill augmentation.
7. If readable CV count exceeds analysis cap, run heuristic prefilter and carry forward only the best candidates plus rescued borderline ones.
8. Score candidates:
   - AI mode if AI job profile exists
   - otherwise heuristic mode
9. If any AI candidate fell back, expose final mode as `ai_with_fallbacks`.
10. Rank candidates.
11. Mark shortlisted candidates.
12. Persist screening batch and eliminated candidates.
13. Update processing time.
14. Return `ScreeningRunResult`.

Important design detail:

- A failure in AI job extraction downgrades the entire screening run to heuristic mode.
- A failure in AI candidate scoring downgrades only that candidate to heuristic fallback.

## 10. Document Extraction Layer

### 10.1 `UploadedCvValidationService`

Source:

- [UploadedCvValidationService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/document/UploadedCvValidationService.java)

Checks:

- at least one uploaded file
- number of non-empty files does not exceed upload cap
- every file is non-empty
- file extension is `.pdf`
- content type is `application/pdf`
- file size does not exceed `recruitment.max-file-size-bytes`

### 10.2 `CvTextExtractionService`

Source:

- [CvTextExtractionService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/document/CvTextExtractionService.java)

Behavior:

- Filters out empty multipart parts
- Routes each file to the first `DocumentExtractionService` that supports it
- Returns a `DocumentExtractionOutcome` per file
- Does not abort the batch if one file fails

### 10.3 `PdfDocumentExtractionService`

Source:

- [PdfDocumentExtractionService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/document/PdfDocumentExtractionService.java)

Behavior:

- Supports PDFs by content type or filename suffix
- Uses PDFBox `Loader.loadPDF(file.getBytes())`
- Uses `PDFTextStripper` to extract text
- Trims text and returns `ExtractedDocument`
- Throws `DocumentExtractionException` on failure

### 10.4 Extraction result model

Sources:

- [DocumentExtractionOutcome.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/document/DocumentExtractionOutcome.java)
- [ExtractedDocument.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/document/ExtractedDocument.java)

Outcome states:

- success: contains extracted document
- failure: contains filename and failure message

Failed extraction outcomes remain in the pipeline and later become zero-score candidate evaluations rather than causing batch failure.

## 11. Heuristic Extraction and Scoring

### 11.1 `TextProfileHeuristicsService`

Source:

- [TextProfileHeuristicsService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/service/TextProfileHeuristicsService.java)

This service underpins most non-AI intelligence.

Responsibilities:

- extract skills from text
- resolve skill aliases
- extract years of experience
- extract keywords
- focus on requirement-heavy lines
- classify requirements into essential, desirable, unclassified

Skill dictionary behavior:

- If `SkillDictionaryProperties` successfully loads `skills.yml`, it uses the configured skills and aliases.
- If not, it falls back to a hardcoded multi-domain default dictionary.

Skill matching logic:

- Normalizes text by lowercasing, removing punctuation, compressing whitespace
- Matches canonical skills
- Matches aliases
- Accepts additional skill terms supplied by sector dictionaries or AI prefilter enrichment

Experience extraction:

- Uses `\b(\d{1,2})\+?\s+years?\b`
- In job description mode it returns the highest detected value
- In CV mode it uses context windows to distinguish personal experience from non-personal text such as company history

Requirement classification:

- Uses lists of essential and desirable hint phrases
- Splits lines into clauses
- Assigns extracted skills to:
  - essential
  - desirable
  - unclassified

Keyword extraction:

- Focuses on lines likely to describe requirements
- Falls back to full text if no requirement-oriented lines are found
- Limits required keywords to eight

### 11.2 `HeuristicJobDescriptionProfileFactory`

Source:

- [HeuristicJobDescriptionProfileFactory.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/service/HeuristicJobDescriptionProfileFactory.java)

Builds `JobDescriptionProfile` using:

- extracted skills
- extracted required keywords
- extracted years of experience

### 11.3 `HeuristicCandidateProfileFactory`

Source:

- [HeuristicCandidateProfileFactory.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/service/HeuristicCandidateProfileFactory.java)

Builds `CandidateProfile` using:

- inferred candidate name
- original filename
- full extracted text
- extracted skills
- estimated years of experience

Name inference strategy:

- Inspect first eight non-blank lines
- Reject lines that look like headings, contact info, or pure skill terms
- Accept lines matching a multi-word person-name pattern
- If no plausible line exists, derive a title-cased name from the filename

### 11.4 `CandidateScoringService`

Source:

- [CandidateScoringService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/service/CandidateScoringService.java)

This service computes the deterministic 0-100 score.

Scoring dimensions:

- essential fit
- broader skill fit
- keyword support
- experience fit
- essential-gap penalty

Outputs:

- `CandidateEvaluation`
- `CandidateScoreBreakdown`
- recruiter-facing heuristic summary

Also supports:

- `scoreForPrefilter(...)` used by large-batch reduction

Prefilter boost detail:

- If AI job extraction identifies must-have skills, matched boosted skills can increase first-pass score modestly
- This affects candidate survival into full analysis, not final persisted scoring directly

### 11.5 `RequirementClassification`

Source:

- [RequirementClassification.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/service/RequirementClassification.java)

This is a small record returned by the heuristics service to carry essential, desirable, and unclassified skill buckets into scoring logic.

## 12. Sector Support

### 12.1 `Sector`

Source:

- [Sector.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/Sector.java)

Supported sectors:

- generic
- IT & Technology
- Healthcare
- Finance
- Education
- Sales & Marketing
- Manual / Labour
- Retail
- Construction
- Manufacturing
- Green Economy

Behavior:

- Converts either enum names or prompt keys from strings
- Unknown values resolve to `GENERIC`

### 12.2 `SectorSkillDictionary`

Source:

- [SectorSkillDictionary.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/SectorSkillDictionary.java)

Purpose:

- Supplement heuristic skill extraction with sector-specific vocabulary absent from the generic dictionary

Use cases:

- heuristic job-profile enrichment
- heuristic candidate-profile enrichment
- more faithful matching for domain-specific terms like `NMC`, `SMSTS`, `HubSpot`, `Lean Manufacturing`

### 12.3 Sector-aware prompts

Prompt files:

- [generic.txt](/Users/miezeh/Desktop/Recruiter/src/main/resources/prompts/generic.txt)
- [it_and_technology.txt](/Users/miezeh/Desktop/Recruiter/src/main/resources/prompts/it_and_technology.txt)
- [healthcare.txt](/Users/miezeh/Desktop/Recruiter/src/main/resources/prompts/healthcare.txt)
- [finance.txt](/Users/miezeh/Desktop/Recruiter/src/main/resources/prompts/finance.txt)
- [education.txt](/Users/miezeh/Desktop/Recruiter/src/main/resources/prompts/education.txt)
- [sales_and_marketing.txt](/Users/miezeh/Desktop/Recruiter/src/main/resources/prompts/sales_and_marketing.txt)
- [manual_labour.txt](/Users/miezeh/Desktop/Recruiter/src/main/resources/prompts/manual_labour.txt)
- [retail.txt](/Users/miezeh/Desktop/Recruiter/src/main/resources/prompts/retail.txt)
- [construction.txt](/Users/miezeh/Desktop/Recruiter/src/main/resources/prompts/construction.txt)
- [manufacturing.txt](/Users/miezeh/Desktop/Recruiter/src/main/resources/prompts/manufacturing.txt)
- [green_economy.txt](/Users/miezeh/Desktop/Recruiter/src/main/resources/prompts/green_economy.txt)

Resolution flow:

- `PromptProviderFactory` asks `PromptLoaderService` for the sector prompt
- prompts are cached in memory
- missing sector-specific files fall back to the generic prompt
- missing generic prompt is fatal

## 13. Deduplication and Batch Reduction

### 13.1 `CvDeduplicationService`

Source:

- [CvDeduplicationService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/service/CvDeduplicationService.java)

Exact duplicate logic:

- Normalize full extracted CV text
- Compute SHA-256 hash
- If a hash was already seen, drop the duplicate

Near-duplicate logic:

- Build a candidate name from the extracted document
- Compare only against already kept candidates with the same normalized candidate name
- Compute Jaccard similarity over token sets with common stop words removed
- If similarity exceeds `0.85`, treat as near duplicate
- If the new candidate text is longer than the kept one, replace the kept one

This design intentionally avoids expensive N^2 comparisons across all candidates.

### 13.2 Reduction / prefiltering

Implemented inside `CandidateScreeningFacade`.

When it triggers:

- Number of readable CVs exceeds `recruitment.analysis-cap`

Algorithm:

1. Score all readable outcomes cheaply using heuristic prefilter scoring.
2. Keep the top `analysisCap` candidates as guaranteed.
3. Optionally rescue some borderline candidates if:
   - they are within `prefilterBorderlineMargin` of the cutoff score, or
   - they match at least half of the job skills and have some experience evidence
4. Append extraction failures back into the analysis list so they remain visible in results.
5. Persist filtered-out readable candidates as eliminated snapshots.

Persisted eliminated-candidate fields:

- candidate name
- candidate filename
- prefilter score
- matched skills

## 14. AI Layer

### 14.1 AI contracts and data records

Sources:

- [JobDescriptionAiExtractor.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/JobDescriptionAiExtractor.java)
- [CandidateAiExtractor.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/CandidateAiExtractor.java)
- [FitAssessmentAiService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/FitAssessmentAiService.java)
- [AiSkillExtractor.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/AiSkillExtractor.java)

Structured records:

- [AiJobDescriptionProfile.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/AiJobDescriptionProfile.java)
- [AiCandidateProfile.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/AiCandidateProfile.java)
- [AiFitAssessment.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/AiFitAssessment.java)
- [RequirementItem.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/RequirementItem.java)
- [EvidenceItem.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/EvidenceItem.java)
- [DimensionJudgement.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/DimensionJudgement.java)
- [ExtractedJobSkills.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/ExtractedJobSkills.java)

Enums defining AI output vocabulary:

- `ConfidenceLevel`
- `ImportanceLevel`
- `RequirementType`
- `MatchBand`
- `JudgementLevel`
- `ExtractionQuality`
- `EvidenceStrength`

### 14.2 Spring AI implementations

Sources:

- [SpringAiJobDescriptionAiExtractor.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/SpringAiJobDescriptionAiExtractor.java)
- [SpringAiCandidateAiExtractor.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/SpringAiCandidateAiExtractor.java)
- [SpringAiFitAssessmentAiService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/SpringAiFitAssessmentAiService.java)
- [SpringAiSkillExtractor.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/SpringAiSkillExtractor.java)

Design:

- Each implementation owns a large system prompt that defines extraction or assessment rules
- Responses are mapped into typed Java records using Spring AI response entity binding
- `SpringAiFitAssessmentAiService` serializes the structured job and candidate profile objects to JSON and passes them as the user payload

### 14.3 AI result wrapping

Sources:

- [AiResult.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/AiResult.java)
- [AiResponseSupport.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/AiResponseSupport.java)
- [TokenUsage.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/TokenUsage.java)
- [TokenUsageAccumulator.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/TokenUsageAccumulator.java)

Purpose:

- Carry both typed AI output and token usage together
- Accumulate prompt/completion token counts safely across concurrent candidate scoring
- Estimate total batch cost from configured per-million-token prices

### 14.4 AI-to-domain mapping

Source:

- [AiAssessmentToCandidateEvaluationMapper.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/AiAssessmentToCandidateEvaluationMapper.java)

Responsibilities:

- Convert bounded AI assessment into the application’s internal score
- Apply configured AI dimension weights
- Apply sector-specific weight overrides
- Apply confidence modifiers
- Produce `CandidateEvaluation` carrying AI explanation, confidence, strengths, gaps, and interview probes

Important design point:

- The LLM does not produce the final numeric ranking score.
- Java calculates the score deterministically from bounded AI outputs.

Fallback behavior:

- If all dimension judgements are null, it can still map from the legacy `MatchBand` plus confidence

## 15. Parallelism and Concurrency

### 15.1 Executor configuration

Source:

- [ConcurrencyConfiguration.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/config/ConcurrencyConfiguration.java)

Bean:

- `screeningVirtualExecutor()` returns `Executors.newVirtualThreadPerTaskExecutor()`

### 15.2 How concurrency is used

AI candidate scoring:

- Each candidate is processed with `CompletableFuture.supplyAsync(..., screeningVirtualExecutor)`
- The facade joins all futures and preserves a collected evaluation list
- Progress counts are updated using `AtomicInteger`
- AI fallback state is tracked with `AtomicBoolean`

Streaming request handling:

- `HomeController` launches the full screening job on a virtual thread so the request can return an `SseEmitter` immediately

Concurrency scope is deliberately narrow:

- heuristic mode is sequential
- AI mode is parallel only at the candidate level
- persistence is still executed as a single transactional save

## 16. Ranking and Shortlisting

### 16.1 `RankingService`

Source:

- [RankingService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/service/RankingService.java)

Sort order:

- descending `score`
- ascending candidate name as tie-breaker

### 16.2 `ShortlistService`

Source:

- [ShortlistService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/service/ShortlistService.java)

Responsibilities:

- resolve requested shortlist count against configured defaults
- resolve requested minimum score against configured defaults
- mark which ranked candidates are shortlisted

Shortlist rule:

- candidate must be within the top N positions
- candidate score must be greater than or equal to the minimum threshold

The service returns a new list of `CandidateEvaluation` records with `shortlisted` set accordingly.

## 17. Domain Model

Sources:

- [CandidateProfile.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/domain/CandidateProfile.java)
- [CandidateScoreBreakdown.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/domain/CandidateScoreBreakdown.java)
- [CandidateEvaluation.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/domain/CandidateEvaluation.java)
- [JobDescriptionProfile.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/domain/JobDescriptionProfile.java)
- [ScreeningResult.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/domain/ScreeningResult.java)
- [ScreeningRunResult.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/domain/ScreeningRunResult.java)
- [ScoringMode.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/domain/ScoringMode.java)
- [ShortlistQuality.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/domain/ShortlistQuality.java)

Key records:

- `CandidateProfile`: extracted candidate facts used for scoring and display
- `CandidateScoreBreakdown`: heuristic-style component scores
- `CandidateEvaluation`: final per-candidate result regardless of scoring path
- `JobDescriptionProfile`: extracted job requirements for heuristic use
- `ScreeningResult`: job profile plus ranked evaluations
- `ScreeningRunResult`: result plus batch metadata, duplicate counts, AI usage, and duration

`ShortlistQuality` thresholds:

- `EXCELLENT`: `90`
- `VERY_GOOD`: `75`
- `GOOD`: `60`
- `ALL`: `40`

## 18. Persistence Architecture

### 18.1 Persistence write model

Entities:

- [ScreeningBatchEntity.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/persistence/ScreeningBatchEntity.java)
- [CandidateEvaluationEntity.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/persistence/CandidateEvaluationEntity.java)
- [EliminatedCandidateEntity.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/persistence/EliminatedCandidateEntity.java)

Relationships:

- `ScreeningBatchEntity` one-to-many `CandidateEvaluationEntity`
- `ScreeningBatchEntity` one-to-many `EliminatedCandidateEntity`

Important persisted batch fields:

- created time
- raw job description text
- shortlist count
- scoring mode
- total CVs received
- candidates scored
- shortlist threshold
- AI prompt/completion/total tokens
- AI estimated cost
- processing time
- AI job description profile JSON
- prompt version string

Important persisted candidate fields:

- candidate name
- candidate filename
- extracted skills joined with newline delimiters
- years of experience
- total score
- heuristic-style score breakdown
- summary
- scoring path
- rank position
- shortlisted flag

Notable persistence gap:

- `CandidateEvaluationEntity` has fields for `aiCandidateProfileJson` and `aiFitAssessmentJson`, but the current save path does not populate them. The schema anticipates richer AI audit storage than the current implementation writes.

### 18.2 Persistence write service

Source:

- [ScreeningBatchPersistenceService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/persistence/ScreeningBatchPersistenceService.java)

Responsibilities:

- create a batch entity
- map domain evaluations to JPA entities
- map eliminated candidate snapshots
- save graph transactionally
- update processing time afterward

### 18.3 Repositories

Sources:

- [ScreeningBatchRepository.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/persistence/ScreeningBatchRepository.java)
- [CandidateEvaluationRepository.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/persistence/CandidateEvaluationRepository.java)

`ScreeningBatchRepository` provides:

- full history ordered by created date
- detailed batch fetch with candidate evaluations
- detailed batch fetch with eliminated candidates
- aggregate AI usage query
- processing-time update query

It uses `@EntityGraph` for eager read access to child collections in history views.

## 19. History Read Model

### 19.1 `ScreeningHistoryService`

Source:

- [ScreeningHistoryService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/persistence/ScreeningHistoryService.java)

Responsibilities:

- convert persisted entities into display-oriented read models
- list historical screening batches
- compute AI usage summary
- reconstruct stored candidate details
- reconstruct eliminated candidates

Important read-model records:

- [ScreeningBatchHistoryItem.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/persistence/ScreeningBatchHistoryItem.java)
- [StoredScreeningBatchResult.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/persistence/StoredScreeningBatchResult.java)
- [StoredCandidateDetail.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/persistence/StoredCandidateDetail.java)
- [StoredEliminatedCandidate.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/persistence/StoredEliminatedCandidate.java)
- [AiUsageSummary.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/persistence/AiUsageSummary.java)

Design nuance:

- Stored job profiles are reconstructed only with raw job description text, not the original extracted heuristic fields.
- Stored candidates are reconstructed from persisted fields and a fallback `CandidateProfileFactory` if necessary.
- History views are display-oriented, not exact serialized snapshots of all in-memory runtime objects.

## 20. Configuration Model

### 20.1 Application YAML

Source:

- [application.yml](/Users/miezeh/Desktop/Recruiter/src/main/resources/application.yml)

Default runtime properties include:

- OpenAI key from `OPENAI_API_KEY`, default `disabled`
- OpenAI model `gpt-4o-mini`
- Spring AI retry max attempts `2`
- in-memory H2 datasource by default
- `ddl-auto: update`
- multipart max file size `1MB`
- multipart max request size `110MB`
- server port `8080`

Recruitment-specific properties:

- shortlist count
- max job description words
- analysis cap
- optional upload-processing cap
- default shortlist quality
- max file size bytes
- default sector
- AI cost pricing

Persistent-H2 profile:

- separate profile `persistent-h2`
- file-based database at `./data/recruiter`

### 20.2 `RecruitmentProperties`

Source:

- [RecruitmentProperties.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/config/RecruitmentProperties.java)

Major property groups:

- top-level screening behavior
- heuristic scoring weights
- AI scoring weights
- AI per-sector overrides
- AI pricing

Validation behavior:

- heuristic weights must sum to `90`
- AI weights must sum to `1.0` within tolerance
- invalid configs fall back to defaults with warnings

### 20.3 `SkillDictionaryProperties`

Source:

- [SkillDictionaryProperties.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/config/SkillDictionaryProperties.java)

Purpose:

- load skills and aliases from YAML
- preserve display names and canonical normalized keys
- allow external skill dictionary configuration without changing Java code

### 20.4 Multipart infrastructure

Source:

- [MultipartConfig.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/config/MultipartConfig.java)

Behavior:

- customizes embedded Tomcat to allow up to `150` parts per request

## 21. Prompt and Skill Assets

### 21.1 Skill dictionary asset

- [skills.yml](/Users/miezeh/Desktop/Recruiter/src/main/resources/skills.yml)

Used by:

- `SkillDictionaryProperties`
- `TextProfileHeuristicsService`

### 21.2 Prompt assets

Used by:

- `PromptLoaderService`
- `PromptProviderFactory`
- `CandidateScreeningFacade` during AI fit assessment

### 21.3 Prompt versioning

Source:

- [AiPromptVersions.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/AiPromptVersions.java)

Purpose:

- attach version identifiers to prompt families
- persist prompt versions used during AI batches

Current prompt IDs:

- `v2-job-extractor-1`
- `v1-job-skill-extractor-1`
- `v2-candidate-extractor-1`
- `v2-fit-assessor-1`

## 22. Error Handling and Degradation Strategy

The system degrades gracefully rather than failing hard in most operational cases.

Examples:

- No OpenAI key: app starts and uses heuristic mode only
- AI job extraction failure: whole batch falls back to heuristic mode
- AI candidate evaluation failure: only that candidate falls back to `heuristic_fallback`
- Invalid or corrupt PDF: candidate is retained in results with score `0.0` and failure summary
- Duplicate uploads: removed before scoring
- Missing skill dictionary file: heuristics service falls back to hardcoded defaults
- Missing sector-specific prompt: falls back to generic prompt
- Missing generic prompt: startup/runtime load failure

## 23. Observability and Logging

Logging is done with SLF4J.

Notable logged events:

- request starts and validation failures
- extraction success/failure per file
- AI enablement and service registration
- sector prompt use
- candidate processing start/end
- AI timing per candidate
- prefilter rescue and elimination details
- batch persistence
- pipeline duration summary

`CandidateScreeningFacade.logPipelineSummary(...)` emits a structured timing overview per batch.

## 24. Test Coverage Shape

Test files present under [src/test/java](/Users/miezeh/Desktop/Recruiter/src/test/java):

- `RecruiterApplicationTests`
- `HomeControllerTest`
- `MultipartUploadExceptionHandlerTest`
- `GlobalExceptionHandlerTest`
- `AiAssessmentToCandidateEvaluationMapperTest`
- `PdfDocumentExtractionServiceTest`
- `CvTextExtractionServiceTest`
- `UploadedCvValidationServiceTest`
- `ScreeningHistoryServiceTest`
- `HeuristicJobDescriptionProfileFactoryTest`
- `HeuristicCandidateProfileFactoryTest`
- `TextProfileHeuristicsServiceTest`
- `CandidateScoringServiceTest`
- `RankingServiceTest`
- `ShortlistServiceTest`
- `CandidateScreeningFacadeTest`
- `CvDeduplicationServiceTest`
- `BatchMetricsFormatterTest`

What the test suite is designed to cover:

- Spring context startup
- controller/model behavior
- upload and exception handling
- heuristic extraction
- name inference
- candidate scoring
- ranking and shortlist behavior
- batch orchestration
- deduplication
- history reconstruction
- AI score mapping

Test configuration:

- [application-test.yml](/Users/miezeh/Desktop/Recruiter/src/test/resources/application-test.yml)
- uses in-memory H2
- disables AI by default in tests
- uses `create-drop` schema lifecycle

## 25. End-to-End Sequence

### Heuristic mode sequence

1. User loads `/`.
2. User enters job description and uploads PDFs.
3. `POST /analyse` binds `ScreeningForm`.
4. `ScreeningFormValidator` validates constraints and uploads.
5. `RerunStore.save()` snapshots the job description and multipart file bytes.
6. `CandidateScreeningFacade.screen(...)` runs.
7. Job description is parsed heuristically.
8. PDFs are text-extracted.
9. Duplicates are removed.
10. Optional large-batch prefilter runs.
11. Each remaining candidate is converted into a `CandidateProfile`.
12. `CandidateScoringService` computes deterministic scores.
13. `RankingService` sorts candidates.
14. `ShortlistService` marks shortlisted candidates.
15. `ScreeningBatchPersistenceService` stores batch data.
16. `results.html` is rendered with `rerunId` in the model; the Rerun Screening button is wired directly by Thymeleaf.

### AI mode sequence

1. User loads `/`.
2. User selects AI mode.
3. Frontend submits to `/analyse/stream`.
4. `HomeController` creates detached temp copies of uploaded files.
5. `RerunStore.save()` snapshots the job description and file bytes from the detached temp files.
6. Background virtual thread starts screening.
7. SSE emits progress updates.
8. Heuristic job profile is created first.
9. AI job extraction attempts to build structured job profile.
10. Optional AI skill extraction enriches prefilter skills.
11. PDFs are extracted and deduplicated.
12. Optional prefilter reduces candidate set.
13. Each candidate is processed in parallel:
    - build heuristic `CandidateProfile`
    - AI candidate extraction
    - AI fit assessment
    - AI-to-score mapping
    - or heuristic fallback if AI fails
14. Ranking, shortlisting, and persistence run.
15. SSE emits `complete` event with redirect URL and `rerunId`.
16. Browser stores `rerunId` in `sessionStorage` keyed by `batchId`, then navigates to `/history/{batchId}`.
17. `results.html` loads; client-side script reads sessionStorage by `batchId` and wires the Rerun Screening button.

## 26. Key Design Strengths

- Clear separation between web, orchestration, extraction, AI, and persistence concerns
- Heuristic mode preserves core functionality without external dependencies
- AI path uses structured outputs instead of raw free-form scoring
- Numeric rank score remains under deterministic Java control
- Large-batch prefiltering controls AI cost and latency
- Candidate-level AI fallback prevents whole-batch failures
- Persisted history supports auditability and later review
- Sector strategy is extensible through prompts and dictionaries

## 27. Current Architectural Limitations

These are based on the current code, not wishlist items.

- The results pages still rely on server-rendered HTML only; there is no REST API for external clients.
- AI candidate profile JSON and AI fit assessment JSON fields exist in the schema but are not currently persisted.
- History reconstruction does not recreate the full original job profile extraction, only the raw job description plus candidate results.
- Heuristic extraction depends heavily on dictionary and regex heuristics; it is intentionally approximate.
- The streaming path relies on temp-file detachment and an in-process background thread, so it is designed for a single-node app rather than a distributed work queue architecture.
- `RerunStore` is in-memory only. Rerun snapshots (including uploaded file bytes) do not survive an application restart. Rerun buttons pointing at evicted entries will silently pre-populate only the job description if the `/rerun/{id}` endpoint returns 404.
- The default JPA setting is `ddl-auto: update`, which is convenient for development but not a formal migration strategy.

## 28. Extension Points

The code already exposes several natural extension seams:

- Add new file formats by implementing `DocumentExtractionService`
- Add new heuristic profile factories by replacing `JobDescriptionProfileFactory` or `CandidateProfileFactory`
- Add new sectors by:
  - extending `Sector`
  - adding a prompt file
  - optionally adding sector skill terms
  - optionally configuring AI scoring overrides
- Add richer persistence of AI audit data by populating existing entity JSON fields
- Replace or augment prompt content without changing Java logic
- Swap heuristic skill dictionary contents through `skills.yml`

## 29. File Index by Architectural Role

Application bootstrap:

- [RecruiterApplication.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/RecruiterApplication.java)

Web:

- [HomeController.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/web/HomeController.java)
- [HistoryController.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/web/HistoryController.java)
- [RerunStore.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/web/RerunStore.java)
- [ScreeningForm.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/web/ScreeningForm.java)
- [ScreeningFormValidator.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/web/ScreeningFormValidator.java)
- [HomePageModelSupport.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/web/HomePageModelSupport.java)
- [MultipartUploadExceptionHandler.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/web/MultipartUploadExceptionHandler.java)
- [GlobalExceptionHandler.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/web/GlobalExceptionHandler.java)

Core services:

- [CandidateScreeningFacade.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/service/CandidateScreeningFacade.java)
- [CandidateScoringService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/service/CandidateScoringService.java)
- [TextProfileHeuristicsService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/service/TextProfileHeuristicsService.java)
- [HeuristicJobDescriptionProfileFactory.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/service/HeuristicJobDescriptionProfileFactory.java)
- [HeuristicCandidateProfileFactory.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/service/HeuristicCandidateProfileFactory.java)
- [CvDeduplicationService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/service/CvDeduplicationService.java)
- [RankingService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/service/RankingService.java)
- [ShortlistService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/service/ShortlistService.java)
- [PipelineTimer.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/service/PipelineTimer.java)
- [ScreeningProgressEvent.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/service/ScreeningProgressEvent.java)
- [ScreeningProgressListener.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/service/ScreeningProgressListener.java)
- [RequirementClassification.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/service/RequirementClassification.java)

Document extraction:

- [CvTextExtractionService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/document/CvTextExtractionService.java)
- [PdfDocumentExtractionService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/document/PdfDocumentExtractionService.java)
- [UploadedCvValidationService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/document/UploadedCvValidationService.java)
- [DocumentExtractionService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/document/DocumentExtractionService.java)
- [DocumentExtractionOutcome.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/document/DocumentExtractionOutcome.java)
- [ExtractedDocument.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/document/ExtractedDocument.java)
- [DocumentExtractionException.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/document/DocumentExtractionException.java)

AI:

- [AiServiceConfiguration.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/AiServiceConfiguration.java)
- [SpringAiJobDescriptionAiExtractor.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/SpringAiJobDescriptionAiExtractor.java)
- [SpringAiCandidateAiExtractor.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/SpringAiCandidateAiExtractor.java)
- [SpringAiFitAssessmentAiService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/SpringAiFitAssessmentAiService.java)
- [SpringAiSkillExtractor.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/SpringAiSkillExtractor.java)
- [AiAssessmentToCandidateEvaluationMapper.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/AiAssessmentToCandidateEvaluationMapper.java)
- [PromptLoaderService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/PromptLoaderService.java)
- [PromptProviderFactory.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/PromptProviderFactory.java)
- [Sector.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/Sector.java)
- [SectorSkillDictionary.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/ai/SectorSkillDictionary.java)

Persistence:

- [ScreeningBatchPersistenceService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/persistence/ScreeningBatchPersistenceService.java)
- [ScreeningHistoryService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/persistence/ScreeningHistoryService.java)
- [ScreeningBatchRepository.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/persistence/ScreeningBatchRepository.java)
- [ScreeningBatchEntity.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/persistence/ScreeningBatchEntity.java)
- [CandidateEvaluationEntity.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/persistence/CandidateEvaluationEntity.java)
- [EliminatedCandidateEntity.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/persistence/EliminatedCandidateEntity.java)

Configuration:

- [RecruitmentProperties.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/config/RecruitmentProperties.java)
- [SkillDictionaryProperties.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/config/SkillDictionaryProperties.java)
- [ConcurrencyConfiguration.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/config/ConcurrencyConfiguration.java)
- [MultipartConfig.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/config/MultipartConfig.java)

Views:

- [index.html](/Users/miezeh/Desktop/Recruiter/src/main/resources/templates/index.html)
- [results.html](/Users/miezeh/Desktop/Recruiter/src/main/resources/templates/results.html)
- [history.html](/Users/miezeh/Desktop/Recruiter/src/main/resources/templates/history.html)
- [candidate-detail.html](/Users/miezeh/Desktop/Recruiter/src/main/resources/templates/candidate-detail.html)
- [eliminated.html](/Users/miezeh/Desktop/Recruiter/src/main/resources/templates/eliminated.html)

## 30. Summary

This application is a server-rendered recruitment screening system built around a single orchestration service. Its architecture intentionally combines:

- deterministic heuristic processing for baseline reliability
- optional AI augmentation for richer structured assessment
- virtual-thread parallelism to control AI latency
- persistence for audit and history
- graceful degradation when AI or upload processing fails

The most important architectural center of gravity is `CandidateScreeningFacade`. The most important supporting subsystems are:

- heuristic text intelligence in `TextProfileHeuristicsService`
- AI extraction and mapping in the `com.recruiter.ai` package
- persistence/history services in `com.recruiter.persistence`
- user interaction flow in `HomeController` plus Thymeleaf templates

Anyone trying to understand the application should start with:

1. [HomeController.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/web/HomeController.java)
2. [CandidateScreeningFacade.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/service/CandidateScreeningFacade.java)
3. [CandidateScoringService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/service/CandidateScoringService.java)
4. [TextProfileHeuristicsService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/service/TextProfileHeuristicsService.java)
5. [ScreeningBatchPersistenceService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/persistence/ScreeningBatchPersistenceService.java)
6. [ScreeningHistoryService.java](/Users/miezeh/Desktop/Recruiter/src/main/java/com/recruiter/persistence/ScreeningHistoryService.java)

