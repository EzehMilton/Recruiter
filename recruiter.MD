# Recruiter - Application Documentation

## 1. What This Application Does

Recruiter is a web-based candidate screening tool built for recruiters. A recruiter pastes a job description, uploads candidate CVs as PDF files, and gets back a ranked shortlist with scores and explanations. Every score is computed deterministically by Java code using heuristic text matching - no LLM decides a numeric score.

The application is a Spring Boot 4 MVP running on Java 21. It uses Thymeleaf for server-side HTML, H2 as an embedded database, and Apache PDFBox for PDF text extraction. There is no JavaScript framework, no external AI service, and no build toolchain beyond Maven.

---

## 2. High-Level Architecture

```
                          Browser (Thymeleaf HTML)
                                  |
                          Spring MVC Controllers
                     HomeController / HistoryController
                                  |
                     CandidateScreeningFacade (orchestrator)
                                  |
          +-----------+-----------+-----------+-----------+
          |           |           |           |           |
   CvTextExtraction  JobProfile  Candidate   Scoring   Persistence
      Service        Factory     ProfileF.   Service     Service
          |           |           |           |           |
   PdfDocument    Heuristics  Heuristics  Heuristics    JPA/H2
   Extraction      Service     Service     Service
```

### Package Structure

| Package | Purpose |
|---------|---------|
| `com.recruiter.web` | Spring MVC controllers, form objects, validators, exception handlers |
| `com.recruiter.screening` | Core screening pipeline: facade, scoring, ranking, shortlisting, profile factories, heuristics |
| `com.recruiter.document` | PDF text extraction, file validation |
| `com.recruiter.domain` | Immutable Java records: `CandidateProfile`, `JobDescriptionProfile`, `CandidateEvaluation`, `ScreeningResult`, etc. |
| `com.recruiter.persistence` | JPA entities, repositories, persistence and history services |
| `com.recruiter.config` | Configuration properties (`RecruitmentProperties`) |

### Technology Stack

| Component | Technology |
|-----------|------------|
| Framework | Spring Boot 4.0.0 |
| Language | Java 21 |
| Templating | Thymeleaf |
| PDF Parsing | Apache PDFBox 3.0.7 |
| Database | H2 (file-based, PostgreSQL-compatible mode) |
| ORM | Spring Data JPA / Hibernate |
| Validation | Jakarta Bean Validation |
| Build | Maven |
| CSS Framework | Bootstrap 5.3.3 (index page only), custom CSS (results and detail pages) |

---

## 3. User Journey: End to End

### Step 1: The Recruiter Opens the Home Page

**URL:** `GET /`
**Controller:** `HomeController.home()`

The controller creates an empty `ScreeningForm` with the default shortlist count (3) from `RecruitmentProperties`, adds form constants (`maxCandidates=20`, `maxWords=1000`) to the model, and returns the `index.html` template.

The recruiter sees a form with three fields: a job description textarea, a shortlist size input, and a file upload for PDFs.

### Step 2: The Recruiter Fills In the Form

The recruiter:
1. Pastes or types a job description into the textarea (up to 1,000 words)
2. Sets how many top candidates they want shortlisted (default: 3)
3. Uploads 1-20 candidate CVs as PDF files (max 5 MB each)

Client-side JavaScript counts words in real time and shows a warning if the limit is exceeded.

### Step 3: The Recruiter Clicks "Analyse Candidates"

**URL:** `POST /analyse`
**Controller:** `HomeController.analyse()`

This is the main action. Here is every class involved, in the exact order they are called:

---

#### 3a. Form Validation

| # | Class | What Happens |
|---|-------|-------------|
| 1 | `HomeController` | Receives the POST, counts uploaded files, logs the request |
| 2 | `ScreeningFormValidator` | Spring calls this via `@InitBinder`. It validates: (a) job description word count <= 1000, (b) shortlist count <= 20, (c) CV files via `UploadedCvValidationService` |
| 3 | `UploadedCvValidationService` | Checks: at least one file, total <= 20, each file is non-empty, has `.pdf` extension, has `application/pdf` content type, size <= 5 MB |

If validation fails, the controller returns the `index.html` template with error messages. The screening pipeline is never entered.

#### 3b. Screening Pipeline

If validation passes, `HomeController` calls `CandidateScreeningFacade.screen()`:

| # | Class | Method | What Happens |
|---|-------|--------|-------------|
| 4 | `CandidateScreeningFacade` | `screen()` | Top-level orchestrator. Calls all subsequent services in sequence. |
| 5 | `HeuristicJobDescriptionProfileFactory` | `create()` | Extracts a structured `JobDescriptionProfile` from the raw job description text. Delegates to `TextProfileHeuristicsService` for skills, keywords, and years of experience. |
| 6 | `TextProfileHeuristicsService` | `extractSkills()` | Scans the text against a dictionary of 18 known technical skills (Java, Spring Boot, AWS, etc.) using case-insensitive phrase matching. |
| 7 | `TextProfileHeuristicsService` | `extractRequiredKeywords()` | Focuses on lines containing requirement hints ("required", "must", "skills", etc.), then extracts tokens >= 4 characters, filtering out stop words. Returns up to 8 keywords. |
| 8 | `TextProfileHeuristicsService` | `extractYearsOfExperience()` | Regex pattern `\b(\d{1,2})\+?\s+years?\b` finds all year mentions, returns the highest number. |
| 9 | `CvTextExtractionService` | `extractAll()` | Iterates over the uploaded `MultipartFile` list, filters out empty files. For each file, resolves an extractor and calls it. |
| 10 | `PdfDocumentExtractionService` | `extract()` | Loads the PDF bytes via PDFBox `Loader.loadPDF()`, strips text via `PDFTextStripper`, returns an `ExtractedDocument` (filename + raw text). If the PDF is corrupt or unreadable, throws `DocumentExtractionException`. |
| 11 | `CvTextExtractionService` | (catch block) | Wraps any `DocumentExtractionException` into a `DocumentExtractionOutcome.failure()` so the pipeline continues for remaining files. |

At this point the facade has a `JobDescriptionProfile` and a `List<DocumentExtractionOutcome>`. It now loops over each outcome:

| # | Class | Method | What Happens |
|---|-------|--------|-------------|
| 12 | `CandidateScreeningFacade` | `buildEvaluation()` | For each extraction outcome: if extraction succeeded, proceeds with profile creation and scoring. If it failed, creates a failed evaluation with score 0.0 and an error summary. |
| 13 | `HeuristicCandidateProfileFactory` | `create()` | Builds a `CandidateProfile` from the extracted text. Infers the candidate's name from the first few lines of the CV (pattern: 2-4 capitalized words, rejecting headers like "Curriculum Vitae"). Falls back to deriving the name from the filename. Calls `TextProfileHeuristicsService` for skills and experience. |
| 14 | `CandidateScoringService` | `evaluate()` | Scores the candidate against the job description. Produces a `CandidateEvaluation` with a numeric score (0-100), a `CandidateScoreBreakdown`, and a human-readable summary. (Scoring mechanism detailed in Section 5.) |

After all candidates are evaluated:

| # | Class | Method | What Happens |
|---|-------|--------|-------------|
| 15 | `RankingService` | `rank()` | Sorts evaluations by score descending (highest first), with candidate name ascending as a tiebreaker for deterministic ordering. |
| 16 | `ShortlistService` | `shortlist()` | Marks the top N candidates as shortlisted, where N is the effective shortlist count. Only candidates with score > 0.0 are eligible. Creates new `CandidateEvaluation` records with the `shortlisted` flag set. |
| 17 | `ScreeningBatchPersistenceService` | `save()` | Persists the entire batch to the database in a single transaction. Creates a `ScreeningBatchEntity` (with the job description text and shortlist count) and one `CandidateEvaluationEntity` per candidate (with name, filename, skills, score breakdown, summary, rank position, and shortlist flag). Returns the generated `batchId`. |

#### 3c. Results Displayed

| # | Class | What Happens |
|---|-------|-------------|
| 18 | `HomeController` | Receives the `ScreeningRunResult` (containing `batchId` and `ScreeningResult`). Adds the screening result, batch ID, shortlist count, and a success message to the model. Returns the `results.html` template. |

The recruiter now sees a ranked list of candidate cards, each showing: rank position, candidate name, score out of 100, shortlist badge (if applicable), and a text summary.

### Step 4: The Recruiter Clicks a Candidate to See Details

**URL:** `GET /history/{batchId}/candidates/{rankPosition}`
**Controller:** `HistoryController.candidateDetail()`

| # | Class | Method | What Happens |
|---|-------|--------|-------------|
| 19 | `HistoryController` | `candidateDetail()` | Receives the batch ID and rank position from the URL path. |
| 20 | `ScreeningHistoryService` | `findCandidate()` | Loads the `ScreeningBatchEntity` from the database (using an `@EntityGraph` to eager-load candidate evaluations). Filters the candidates to find the one matching the requested rank position. Reconstructs a `CandidateEvaluation` domain object from the stored entity. |
| 21 | `ScreeningHistoryService` | `toCandidateEvaluation()` | Converts the JPA entity back to the domain record: rebuilds `CandidateProfile` (with name, filename, skills list, years of experience) and `CandidateScoreBreakdown` (skill, keyword, experience components). |
| 22 | `HistoryController` | (model setup) | Adds the `candidateEvaluation`, `batchId`, `batchCreatedAtDisplay`, `shortlistCount`, and `rankPosition` to the model. Returns the `candidate-detail.html` template. |

The recruiter sees the full detail page: candidate name, match strength indicator (strong/medium/weak based on score thresholds of 75/50), shortlist status, score breakdown with progress bars for each component, extracted skills as chips, estimated experience, source filename, and the full summary text.

---

## 4. Complete Class Call Chain (Summary)

From form submission to candidate detail, the classes involved in order are:

```
HomeController.analyse()
  -> ScreeningFormValidator.validate()
       -> UploadedCvValidationService.validate()
  -> CandidateScreeningFacade.screen()
       -> HeuristicJobDescriptionProfileFactory.create()
            -> TextProfileHeuristicsService.extractSkills()
            -> TextProfileHeuristicsService.extractRequiredKeywords()
            -> TextProfileHeuristicsService.extractYearsOfExperience()
       -> CvTextExtractionService.extractAll()
            -> PdfDocumentExtractionService.extract()  [per file]
       -> [per candidate]:
            -> HeuristicCandidateProfileFactory.create()
                 -> TextProfileHeuristicsService.extractSkills()
                 -> TextProfileHeuristicsService.extractYearsOfExperience()
            -> CandidateScoringService.evaluate()
                 -> CandidateScoringService.score()          [private]
                 -> CandidateScoringService.buildSummary()   [private]
       -> RankingService.rank()
       -> ShortlistService.shortlist()
       -> ScreeningBatchPersistenceService.save()

HistoryController.candidateDetail()
  -> ScreeningHistoryService.findCandidate()
       -> ScreeningBatchRepository.findDetailedById()
       -> ScreeningHistoryService.toCandidateEvaluation()
```

---

## 5. Scoring Mechanism

All scoring is deterministic. Java code computes every number. There is no randomness, no LLM involvement, and no external API call.

### 5.1 Score Components

The total score ranges from 0 to 100 and is the sum of three weighted components:

| Component | Max Points | What It Measures |
|-----------|-----------|-----------------|
| **Skills fit** | 70 | How many of the job's required technical skills appear in the candidate's CV |
| **Keyword fit** | 20 | How many contextual keywords from the job description's requirement sections appear in the candidate's CV text |
| **Experience fit** | 10 | How the candidate's years of experience compare to the job's requirement |

### 5.2 Skills Score (0-70 points)

The system maintains a dictionary of 18 known technical skills:
> Java, Spring, Spring Boot, Python, JavaScript, TypeScript, React, SQL, PostgreSQL, MySQL, AWS, Docker, Kubernetes, Microservices, REST APIs, Maven, Git, HTML, CSS

Both the job description and the CV are scanned against this dictionary using case-insensitive phrase matching. The score is:

```
if no skills were extracted from the job description:
    if the candidate has any known skills -> 30 points (generous fallback)
    otherwise -> 0 points
else:
    score = (number of matched skills / number of job skills) * 70
```

**Example:** The job requires Java, Spring Boot, and AWS (3 skills). The candidate's CV contains Java and AWS (2 matched). Score = (2/3) * 70 = 46.7 points.

### 5.3 Keyword Score (0-20 points)

Keywords are tokens of 4+ characters extracted from requirement-related lines in the job description (lines containing words like "required", "must", "skills", "qualifications"), after removing stop words. Up to 8 keywords are used.

The candidate's CV text is tokenized the same way. The score is:

```
if no job keywords or no keyword matches -> 0 points
else:
    denominator = min(number of job keywords, 10)
    score = (number of matched keywords / denominator) * 20
```

The denominator cap at 10 prevents the score from becoming artificially low when a job description produces many keywords.

### 5.4 Experience Score (0-10 points)

Both the job description and the CV are scanned for patterns like "5 years", "7+ years", etc. The highest number found in each is used.

```
if the job has no experience requirement, or the candidate has none -> 0 points
else:
    ratio = min(candidate years / required years, 1.0)
    score = ratio * 10
```

Exceeding the requirement is not penalized - it caps at 10 points. If the candidate has 8 years and the job requires 5, the ratio is capped at 1.0 and the score is 10.

### 5.5 Final Score

```
totalScore = clamp(skillScore + keywordScore + experienceScore, 0, 100)
```

The result is rounded to one decimal place. This score is the single ranking criterion.

### 5.6 Score Breakdown Record

The three components are stored in a `CandidateScoreBreakdown` record alongside the total. This breakdown is persisted to the database and displayed on the candidate detail page as progress bars (each bar's width is proportional to its component maximum).

### 5.7 Summary Generation

After scoring, `CandidateScoringService.buildSummary()` generates a deterministic text summary:

1. Reports matched skills (e.g., "Matched 3 important terms: Java, Spring Boot, AWS.")
2. Highlights up to 3 missing skills (e.g., "Weak match on Docker, Kubernetes.")
3. Reports matched keywords (e.g., "Also matched required keywords: backend, distributed.")
4. States estimated experience (e.g., "Estimated experience: 6 years.")
5. Ends with the total score (e.g., "Deterministic score: 76.7/100.")

If the job description had no extractable requirements, the summary notes: "No structured job requirements were detected, so the score stays conservative."

### 5.8 Match Strength

The candidate detail page classifies scores into three tiers using Thymeleaf logic:

| Score Range | Label | Colour |
|-------------|-------|--------|
| >= 75 | Strong match | Green |
| >= 50 | Medium match | Amber |
| < 50 | Weak match | Grey |

---

## 6. The Role of LLMs

**There are currently no LLMs in this application.**

The project description references "AI-powered" screening, and the codebase is structured to accommodate LLM integration in the future (the `JobDescriptionProfileFactory` and `CandidateProfileFactory` interfaces exist as extension points), but the current implementation is entirely heuristic.

Specifically:
- There is no `com.recruiter.ai` package.
- There are no prompt templates in `src/main/resources/prompts/`.
- There are no OpenAI, Anthropic, or other LLM client dependencies in `pom.xml`.
- `application.yml` contains no AI configuration.
- Every score, summary, and profile extraction is computed by `TextProfileHeuristicsService` using regex patterns and a hardcoded skill dictionary.

### Where LLMs Could Plug In

The factory interfaces (`CandidateProfileFactory`, `JobDescriptionProfileFactory`) and the service interfaces use a strategy pattern. An LLM-based implementation could:

1. **Replace `HeuristicJobDescriptionProfileFactory`** with an LLM extractor that parses the job description into structured fields (required skills, preferred skills, qualifications, etc.) with greater accuracy than regex.
2. **Replace `HeuristicCandidateProfileFactory`** with an LLM extractor that reads CV text and returns structured candidate data (name, skills, qualifications, experience) more reliably than pattern matching.
3. **Add an LLM summary generator** that produces natural-language summaries grounded in the score breakdown, rather than the current template-based approach.

In all cases, the scoring would remain in Java. The LLM would extract structured data; Java would compute the numbers. This is by design - it keeps scores deterministic, reproducible, and auditable.

---

## 7. Data Model

### Database Tables

**`screening_batches`**

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK) | Auto-generated |
| `created_at` | TIMESTAMP | Set on insert |
| `job_description_text` | TEXT | Full job description |
| `shortlist_count` | INT | Effective shortlist size used |

**`candidate_evaluations`**

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK) | Auto-generated |
| `screening_batch_id` | BIGINT (FK) | References `screening_batches.id` |
| `candidate_name` | VARCHAR(255) | Inferred or extracted name |
| `candidate_filename` | VARCHAR(255) | Original PDF filename |
| `extracted_skills` | TEXT | Newline-separated skill names |
| `years_of_experience` | INT | Nullable |
| `score` | DECIMAL(5,1) | Total score 0-100 |
| `skill_score` | DECIMAL(5,1) | Component: skills (0-70) |
| `keyword_score` | DECIMAL(5,1) | Component: keywords (0-20) |
| `experience_score` | DECIMAL(5,1) | Component: experience (0-10) |
| `summary` | TEXT | Human-readable evaluation |
| `rank_position` | INT | 1-based rank in batch |
| `shortlisted` | BOOLEAN | Whether candidate was selected |

---

## 8. Configuration Reference

All configuration lives in `application.yml` under the `recruitment` prefix:

| Property | Default | Description |
|----------|---------|-------------|
| `recruitment.shortlist-count` | 3 | Default number of candidates to shortlist |
| `recruitment.max-job-description-words` | 1000 | Maximum word count for job description input |
| `recruitment.max-candidates` | 20 | Maximum number of CV files per submission |
| `recruitment.max-file-size-bytes` | 5242880 | Maximum size per uploaded file (5 MB) |

---

## 9. Pages and Routes

| Route | Method | Controller | Template | Description |
|-------|--------|------------|----------|-------------|
| `/` | GET | `HomeController.home()` | `index.html` | Screening form |
| `/analyse` | POST | `HomeController.analyse()` | `results.html` | Run screening and show results |
| `/history` | GET | `HistoryController.history()` | `history.html` | List all past screening batches |
| `/history/{batchId}` | GET | `HistoryController.historyDetail()` | `results.html` | View results for a specific batch |
| `/history/{batchId}/candidates/{rank}` | GET | `HistoryController.candidateDetail()` | `candidate-detail.html` | View a single candidate's full detail |
