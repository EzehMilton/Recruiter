# Recruiter - Application Documentation

## 1. What This Application Does

Recruiter is a web-based candidate screening tool. A recruiter pastes a job description, uploads candidate CVs as PDF files, chooses a scoring mode, and gets a ranked shortlist with match quality labels and explanations.

The application supports two scoring paths:

- **AI mode** — uses OpenAI (via Spring AI) to extract structured job and candidate profiles, then performs a bounded fit assessment. The LLM never chooses a numeric score. Java code maps the AI assessment into a stable internal score. AI candidates are scored in parallel using virtual threads, with live progress streamed to the browser via Server-Sent Events.
- **Heuristic mode** — uses local text matching against a cross-domain skill dictionary (190+ skills). Fully deterministic, works without any API key.

Both paths produce an internal 0-100 score used for ranking and shortlist eligibility. This score is never shown to the recruiter. Instead, candidates see a human-readable match label (Excellent / Strong / Good / Partial / Weak). The recruiter selects a shortlist size and a quality threshold from a dropdown. Only candidates meeting both criteria are shortlisted.

When uploads exceed the analysis cap, a fast first-pass heuristic filter reduces the set before full analysis. Duplicate files are detected and removed. Corrupt PDFs do not crash the batch. Eliminated candidates from pre-filtering are persisted separately and viewable.

### Technology Stack

| Component | Technology |
|-----------|------------|
| Framework | Spring Boot 4.0.0 |
| Language | Java 21 (virtual threads for parallel AI scoring) |
| AI | Spring AI 2.0.0-M4 (OpenAI, gpt-4o-mini) |
| Templating | Thymeleaf |
| PDF Parsing | Apache PDFBox 3.0.7 |
| Database | H2 (file-based, PostgreSQL-compatible mode) |
| ORM | Spring Data JPA / Hibernate |
| Streaming | Server-Sent Events (SSE) for AI progress |
| Validation | Jakarta Bean Validation |
| Build | Maven |
| CSS | Bootstrap 5.3.3 (form page), custom CSS (results/detail pages) |

---

## 2. High-Level Architecture

```
                          Browser (Thymeleaf HTML)
                                  |
                    +-------------+-------------+
                    |                           |
             HomeController              HistoryController
             POST /analyse                GET /history/*
             POST /analyse/stream (SSE)
                    |
         CandidateScreeningFacade (orchestrator)
                    |
    +-------+-------+-------+-------+-------+-------+
    |       |       |       |       |       |       |
  CvText  Dedup  PreFilter JobProf CandProf Score  Persist
  Extract        Reduce   Factory  Factory  Service Service
    |                        |       |       |       |
  PDFBox              +------+-------+       |    JPA/H2
                      |              |       |
               [Heuristic]    [Optional AI]  |
               TextProfile    SpringAi*      |
               Heuristics     Extractors     |
                Service            |         |
                              FitAssessment  |
                              AiService      |
                                   |         |
                              AiAssessment   |
                              ToCandidate    |
                              Mapper         |
                                             |
                                    Ranking + Shortlist
```

### Package Structure

| Package | Purpose |
|---------|---------|
| `com.recruiter.web` | Controllers, form, validators, exception handlers, SSE streaming |
| `com.recruiter.service` | Core pipeline: facade, scoring, ranking, shortlisting, profile factories, heuristics, progress events |
| `com.recruiter.ai` | AI domain records, enums, extractor interfaces, Spring AI implementations, mapper, token tracking, configuration |
| `com.recruiter.document` | PDF text extraction, file validation |
| `com.recruiter.domain` | Immutable records: `CandidateEvaluation`, `CandidateProfile`, `ScreeningResult`, `ScoringMode`, `ShortlistQuality`, etc. |
| `com.recruiter.persistence` | JPA entities, repositories, persistence and history services, eliminated candidate tracking |
| `com.recruiter.config` | Configuration properties |
| `com.recruiter.support` | Formatting utilities (batch metrics, token usage display) |

---

## 3. User Journey

### Step 1: The Recruiter Opens the Form

**URL:** `GET /`

The form has five controls:
1. **Job description** textarea (up to 1,000 words, live word count)
2. **Shortlist size** (default: 3)
3. **Shortlist quality threshold** dropdown — Excellent only (90) / Very good and above (75) / Good and above (60) / All viable candidates (40)
4. **Scoring mode** radio buttons — AI / Heuristic
5. **CV file upload** (PDF, up to the operational upload cap, default 500)

### Step 2: The Recruiter Submits

**Heuristic mode** submits synchronously via `POST /analyse`. Results appear immediately.

**AI mode** submits asynchronously via `POST /analyse/stream`. The form is replaced by a progress overlay showing:
- Phase messages ("Extracting text...", "Pre-filtering candidates...", "Analysing candidate 3 of 10...")
- A progress bar that fills as candidates complete
- The name of the last completed candidate

On completion, the browser redirects to the results page.

### Step 3: The Screening Pipeline

The `CandidateScreeningFacade` orchestrates these stages:

| Stage | What Happens |
|-------|-------------|
| **1. Resolve settings** | Effective shortlist count, minimum score (from quality dropdown), and scoring mode. If AI requested but not configured, falls back to heuristic with a logged warning. |
| **2. Extract text** | `CvTextExtractionService` extracts text from all PDFs via PDFBox. Corrupt files become failure outcomes — they don't crash the batch. |
| **3. Deduplicate** | Files with identical content are detected and removed. The count of removed duplicates is tracked. |
| **4. Pre-filter** | If readable CVs exceed `analysis-cap` (default 20), all are scored cheaply with heuristic matching. Only the top N proceed. Eliminated candidates are persisted separately with their pre-filter score and matched skills. |
| **5. Extract job profile** | Heuristic mode: `TextProfileHeuristicsService` extracts skills, keywords, experience. AI mode: `SpringAiJobDescriptionAiExtractor` sends the JD to OpenAI for structured extraction. If AI job extraction fails, the entire batch falls back to heuristic. |
| **6. AI skill boost** | In AI mode, `SpringAiSkillExtractor` extracts 10-15 domain-specific terms from the JD. These are merged with heuristic-extracted skills to improve the first-pass filter and heuristic fallback coverage. |
| **7. Score candidates** | **Heuristic:** sequential, deterministic scoring (see Section 5). **AI:** parallel scoring using virtual threads — all candidates scored concurrently. Per candidate: `SpringAiCandidateAiExtractor` extracts a structured profile, then `SpringAiFitAssessmentAiService` produces a bounded fit assessment. `AiAssessmentToCandidateEvaluationMapper` converts it to an internal score. If AI fails for one candidate, that candidate falls back to heuristic individually. |
| **8. Rank** | `RankingService` sorts by score descending, then name ascending as tiebreaker. |
| **9. Shortlist** | `ShortlistService` marks the top N candidates as shortlisted, but only if their score >= the quality threshold. If nobody meets the threshold, nobody is shortlisted. |
| **10. Persist** | `ScreeningBatchPersistenceService` saves the batch with all evaluations, AI audit JSON, prompt versions, token usage, processing time, and eliminated candidates. Returns `batchId`. |

### Step 4: Results Page

The recruiter sees:
- Batch metadata (ID, scoring mode, shortlist criteria label, CV counts, AI token usage and estimated cost if applicable)
- If pre-filtering occurred: "Received 150 CVs. Reduced to the top 20 candidates using a fast first-pass relevance filter."
- If duplicates were removed: a notification with the count
- If nobody was shortlisted: "No candidates met the shortlist threshold."
- Ranked candidate cards showing:
  - Rank and candidate name
  - Scoring-path badge (AI / Heuristic / Heuristic fallback)
  - Colour-coded match label (Excellent / Strong / Good / Partial / Weak)
  - Shortlist outcome ("Shortlisted" or "Not shortlisted") — shown separately from match quality
  - Summary text

No raw numeric score is displayed. A candidate may show "Good match" + "Not shortlisted" if the threshold is stricter.

### Step 5: Candidate Detail Page

**URL:** `GET /history/{batchId}/candidates/{rankPosition}`

The detail page renders differently by scoring path:

**AI-scored candidates** show: overall match bar, confidence level, top strengths (green tags), top gaps (red tags), interview probe areas (blue tags), and the recruiter-facing explanation.

**Heuristic-scored candidates** show: skills fit bar (0-70), keyword fit bar (0-20), experience fit bar (0-10), extracted skills as chips, and estimated experience.

Both show: candidate name, match strength indicator, shortlist status, source filename, and summary.

### Step 6: Eliminated Candidates

**URL:** `GET /history/{batchId}/eliminated`

When pre-filtering occurred, the recruiter can view a table of eliminated candidates showing their pre-filter score and matched skills.

---

## 4. Complete Class Call Chain

```
HomeController.analyse() / analyseWithStream()
  -> ScreeningFormValidator.validate()
       -> UploadedCvValidationService.validate()
  -> CandidateScreeningFacade.screen()
       -> ShortlistService.resolveShortlistCount() / resolveMinimumScore()
       -> resolveEffectiveScoringMode()
       -> CvTextExtractionService.extractAll()
            -> PdfDocumentExtractionService.extract()  [per file]
       -> deduplicateOutcomes()
       -> HeuristicJobDescriptionProfileFactory.create()
            -> TextProfileHeuristicsService.extractSkills()
            -> TextProfileHeuristicsService.extractRequiredKeywords()
            -> TextProfileHeuristicsService.extractYearsOfExperience()
       -> reduceToAnalysisCap()  [if > analysisCap readable CVs]
            -> CandidateScoringService.evaluate()  [cheap first-pass per CV]
       -> [AI mode]:
            SpringAiJobDescriptionAiExtractor.extract()  [once]
            SpringAiSkillExtractor.extract()  [once, for skill boost]
            [parallel, per candidate via virtual threads]:
                 SpringAiCandidateAiExtractor.extract()
                 SpringAiFitAssessmentAiService.assess()
                 AiAssessmentToCandidateEvaluationMapper.map()
                 [on failure: heuristic fallback for that candidate]
       -> [Heuristic mode]:
            [sequential, per candidate]:
                 HeuristicCandidateProfileFactory.create()
                 CandidateScoringService.evaluate()
       -> RankingService.rank()
       -> ShortlistService.shortlist()
       -> ScreeningBatchPersistenceService.save()

HistoryController.candidateDetail()
  -> ScreeningHistoryService.findCandidate()
       -> ScreeningBatchRepository.findDetailedById()
       -> ScreeningHistoryService.toCandidateEvaluation()
```

---

## 5. Heuristic Scoring

Deterministic scoring from three weighted components:

| Component | Max Points | What It Measures |
|-----------|-----------|-----------------|
| **Skills fit** | 70 | How many of the job's skills appear in the CV |
| **Keyword fit** | 20 | How many contextual keywords from requirement sections appear in the CV |
| **Experience fit** | 10 | How candidate's years compare to the requirement |

### Skills (0-70)

The system has a dictionary of 190+ known skills across: technical (Java, Python, AWS, Docker...), healthcare (Patient Care, Nursing...), trades (Carpentry, Welding...), finance (Accounting, SAP...), education (Teaching, Curriculum...), hospitality (Food Hygiene, Barista...), legal (Conveyancing, GDPR...), logistics (Warehouse, Supply Chain...), creative (Graphic Design, Figma...), and general transferable (Project Management, Communication...).

```
if no skills extracted from JD:
    if candidate has any known skills -> 30 points
    otherwise -> 0
else:
    score = (matched / total job skills) * 70
```

### Keywords (0-20)

```
score = (matched keywords / min(job keywords, 10)) * 20
```

### Experience (0-10)

```
score = min(candidate years / required years, 1.0) * 10
```

### Final: `clamp(skills + keywords + experience, 0, 100)`, rounded to 1 decimal.

---

## 6. AI Scoring

The LLM never produces a numeric score. Three stages:

1. **Structured extraction** — `AiJobDescriptionProfile` and `AiCandidateProfile` are extracted as typed JSON with fields for requirements, evidence strength, extraction quality, and ambiguities.
2. **Bounded fit assessment** — `AiFitAssessment` contains: `MatchBand` (STRONG_MATCH / POSSIBLE_MATCH / WEAK_MATCH / NOT_RECOMMENDED), `ConfidenceLevel` (HIGH / MEDIUM / LOW), dimension judgements, strengths, gaps, interview probe areas, and a recruiter-facing explanation.
3. **Java mapping** — `AiAssessmentToCandidateEvaluationMapper` converts MatchBand + ConfidenceLevel to a stable internal score:

| MatchBand | HIGH | MEDIUM | LOW |
|-----------|------|--------|-----|
| STRONG_MATCH | 90 | 85 | 80 |
| POSSIBLE_MATCH | 72 | 68 | 62 |
| WEAK_MATCH | 48 | 42 | 35 |
| NOT_RECOMMENDED | 20 | 15 | 10 |

### Parallel Execution

AI candidates are scored concurrently using Java 21 virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`). The two LLM calls per candidate (extract + assess) are sequential relative to each other, but all candidates run in parallel. A `TokenUsageAccumulator` (thread-safe via `AtomicLong`) tracks total prompt/completion tokens across all parallel calls.

### Per-Candidate Fallback

If AI fails for one candidate, that candidate falls back to heuristic. Others continue with AI. The batch mode becomes `ai_with_fallbacks`. Each `CandidateEvaluation` records its `scoringPath`: `"ai"`, `"heuristic"`, or `"heuristic_fallback"`.

### Prompt Versions

Tracked as constants: `v2-job-extractor-1`, `v2-candidate-extractor-1`, `v2-fit-assessor-1`. Persisted per batch for audit.

### Conditional Activation

AI services are only created when `OPENAI_API_KEY` is set. The `@ConditionalOnExpression` on `AiServiceConfiguration` gates this. Without the key, the app starts in heuristic-only mode.

### Token Usage and Cost

Each AI call's token usage is captured. The batch total is persisted and displayed on the results page. Estimated cost is calculated from configurable per-million-token prices (default: $0.15 prompt, $0.60 completion for gpt-4o-mini).

---

## 7. Match Labels and Shortlist Quality

### Match Labels (displayed on candidate cards)

| Internal Score | Label | Colour |
|---------------|-------|--------|
| 90+ | Excellent match | Dark green |
| 75–89.9 | Strong match | Green |
| 60–74.9 | Good match | Blue |
| 40–59.9 | Partial match | Amber |
| Below 40 | Weak match | Grey |

### Shortlist Quality Threshold (selected by recruiter)

| Level | Threshold | Label |
|-------|-----------|-------|
| `EXCELLENT` | 90 | Excellent only |
| `VERY_GOOD` | 75 | Very good and above |
| `GOOD` | 60 | Good and above |
| `ALL` | 40 | All viable candidates |

Match quality and shortlist outcome are independent. A "Good match" (score 65) is "Not shortlisted" if the threshold is "Very good and above" (75).

---

## 8. First-Pass Reduction

When readable CVs exceed `analysis-cap` (default 20):

1. All PDFs are extracted
2. Duplicates removed
3. Each readable CV scored cheaply with heuristic matching
4. Top N proceed to full analysis
5. Eliminated candidates persisted with pre-filter score and matched skills

This is a recall step only — it does not decide shortlisting. The results page shows "Received 150 CVs. Reduced to the top 20."

---

## 9. SSE Progress Streaming

When AI mode is selected, the form submits via JavaScript to `POST /analyse/stream` which returns Server-Sent Events:

| Event | Data |
|-------|------|
| `progress` | Phase, completed count, total, candidate name, message |
| `complete` | Batch ID, redirect URL |
| `error` | Error message |

Phases: `extracting` → `deduplicating` → `prefiltering` → `scoring` → `finalising` → `complete`

The progress overlay shows a progress bar and candidate names as they complete. On `complete`, the browser redirects to `/history/{batchId}`.

File bytes are snapshotted into memory during upload so they survive across the two HTTP requests (upload → stream). If the client disconnects, screening still completes and results are available in history.

---

## 10. Data Model

### screening_batches

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK) | Auto-generated |
| `created_at` | TIMESTAMP | Set on insert |
| `job_description_text` | TEXT | Full job description |
| `shortlist_count` | INT | Effective shortlist size |
| `scoring_mode` | VARCHAR(30) | `ai`, `heuristic`, or `ai_with_fallbacks` |
| `total_cvs_received` | INT | Total files uploaded |
| `duplicate_cvs_removed` | INT | Duplicates detected |
| `candidates_scored` | INT | Candidates that went through full analysis |
| `shortlist_threshold` | DECIMAL(5,1) | Minimum score for shortlisting |
| `ai_job_description_profile_json` | TEXT | Raw AI job extraction JSON (nullable) |
| `prompt_versions` | VARCHAR(100) | Prompt version identifiers (nullable) |
| `ai_prompt_tokens` | INT | Total AI prompt tokens (nullable) |
| `ai_completion_tokens` | INT | Total AI completion tokens (nullable) |
| `ai_estimated_cost_usd` | DECIMAL(10,4) | Estimated AI cost (nullable) |
| `processing_time_ms` | BIGINT | Total pipeline time in ms (nullable) |

### candidate_evaluations

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK) | Auto-generated |
| `screening_batch_id` | BIGINT (FK) | References `screening_batches.id` |
| `candidate_name` | VARCHAR(255) | Inferred or extracted name |
| `candidate_filename` | VARCHAR(255) | Original PDF filename |
| `extracted_skills` | TEXT | Newline-separated skill names |
| `years_of_experience` | INT | Nullable |
| `score` | DECIMAL(5,1) | Internal score 0-100 |
| `skill_score` | DECIMAL(5,1) | Heuristic: skills (0-70) |
| `keyword_score` | DECIMAL(5,1) | Heuristic: keywords (0-20) |
| `experience_score` | DECIMAL(5,1) | Heuristic: experience (0-10) |
| `scoring_path` | VARCHAR(30) | `ai`, `heuristic`, or `heuristic_fallback` |
| `summary` | TEXT | Human-readable evaluation |
| `ai_candidate_profile_json` | TEXT | Raw AI extraction JSON (nullable) |
| `ai_fit_assessment_json` | TEXT | Raw AI assessment JSON (nullable) |
| `rank_position` | INT | 1-based rank in batch |
| `shortlisted` | BOOLEAN | Whether candidate was selected |

### eliminated_candidates

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK) | Auto-generated |
| `screening_batch_id` | BIGINT (FK) | References `screening_batches.id` |
| `candidate_name` | VARCHAR(255) | Inferred name |
| `candidate_filename` | VARCHAR(255) | Original PDF filename |
| `pre_filter_score` | DECIMAL(5,1) | Heuristic score from first-pass |
| `matched_skills` | TEXT | Skills that matched (newline-separated) |

---

## 11. Configuration

### Application Properties (`recruitment.*`)

| Property | Default | Description                                                                                    |
|----------|---------|------------------------------------------------------------------------------------------------|
| `shortlist-count` | 3 | Default shortlist size                                                                         |
| `max-job-description-words` | 1000 | Max word count for JD input                                                                    |
| `analysis-cap` | 20 | Max candidates for full analysis after pre-filtering                                           |
| `upload-processing-cap` | 500 (if unset) | Operational upload safety ceiling                                                              |
| `default-shortlist-quality` | `VERY_GOOD` | Default quality threshold. One of: `EXCELLENT` (90), `VERY_GOOD` (75), `GOOD` (60), `ALL` (40) |
| `max-file-size-bytes` | 1048576 | Max size per file (1 MB)                                                                       |
| `ai-cost.prompt-price-per-million` | 0.15 | Cost per million prompt tokens (USD)                                                           |
| `ai-cost.completion-price-per-million` | 0.60 | Cost per million completion tokens (USD)                                                       |

### Spring AI Properties

| Property | Default | Description |
|----------|---------|-------------|
| `spring.ai.openai.api-key` | `disabled` | Set via `OPENAI_API_KEY` env var. "disabled" = no AI services. |
| `spring.ai.openai.chat.options.model` | `gpt-4o-mini` | OpenAI model |
| `spring.ai.retry.max-attempts` | 2 | Retry attempts for AI calls |

---

## 12. Pages and Routes

| Route | Method | Controller | Template | Description |
|-------|--------|------------|----------|-------------|
| `/` | GET | `HomeController` | `index.html` | Screening form |
| `/analyse` | POST | `HomeController` | `results.html` | Synchronous screening (heuristic) |
| `/analyse/stream` | POST | `HomeController` | SSE stream | Async AI screening with progress |
| `/history` | GET | `HistoryController` | `history.html` | List past batches |
| `/history/{batchId}` | GET | `HistoryController` | `results.html` | View batch results |
| `/history/{batchId}/candidates/{rank}` | GET | `HistoryController` | `candidate-detail.html` | Candidate detail |
| `/history/{batchId}/eliminated` | GET | `HistoryController` | `eliminated.html` | Pre-filtered candidates |

---

## 13. Effective Scoring Modes

| Requested | AI Configured? | Job Extraction OK? | All Candidates OK? | Effective Mode |
|-----------|---------------|-------------------|-------------------|----------------|
| `ai` | Yes | Yes | Yes | `ai` |
| `ai` | Yes | Yes | Some fail | `ai_with_fallbacks` |
| `ai` | Yes | No | — | `heuristic` |
| `ai` | No | — | — | `heuristic` |
| `heuristic` | — | — | — | `heuristic` |
