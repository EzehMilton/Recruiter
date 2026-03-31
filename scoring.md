# Scoring System

This document explains how candidates are scored in both the **Heuristic** and **AI** paths, the end-to-end screening pipeline, and lists every class involved.

---

## Overview

When a recruiter submits a job description and a set of CVs, the application:

1. Extracts structured data from the job description and each CV
2. Scores every candidate against the job requirements
3. Ranks candidates by score and selects a shortlist

There are two scoring engines. The recruiter picks one via the form (`scoringMode`):

| Mode | What happens |
|---|---|
| `heuristic` | Deterministic, rule-based scoring. No API calls. |
| `ai` | AI extracts structured profiles and judges fit. Falls back to heuristic per-candidate on failure. |
| `ai_with_fallbacks` | Same as `ai`, but explicitly signals that at least one candidate fell back to heuristic. |

---

## End-to-End Pipeline

```
User submits form (job description + CVs + settings)
                        |
                        v
              +-------------------+
              | 1. Resolve config |  shortlist count, minimum score, scoring mode
              +-------------------+
                        |
                        v
              +-----------------------------+
              | 2. Extract job profile      |  Heuristic text extraction:
              |    (always heuristic first) |  skills, keywords, years of experience
              +-----------------------------+
                        |
                        v
              +------------------------+
              | 3. Extract CV text     |  PDF -> raw text for each uploaded file
              +------------------------+
                        |
                        v
              +-------------------+
              | 4. Deduplicate    |  Remove CVs with same filename or same
              |                   |  text fingerprint (first 500 chars hashed)
              +-------------------+
                        |
                        v
              +-----------------------------+
              | 5. Pre-filter (if needed)   |  Triggered when readable CVs > analysisCap (default 20).
              |                             |  Scores ALL candidates heuristically, keeps the top N,
              |                             |  stores eliminated candidates separately.
              +-----------------------------+
                        |
                        v
            +-----------+-----------+
            |                       |
       mode = heuristic        mode = ai
            |                       |
            v                       v
   +------------------+    +---------------------------+
   | 6a. Score each   |    | 6b. AI job extraction     |
   |     candidate    |    |     (structured profile   |
   |     sequentially |    |      via LLM)             |
   |     (heuristic)  |    +---------------------------+
   +------------------+                |
            |                          v
            |               +---------------------------+
            |               | 6c. Score each candidate  |
            |               |     in parallel:          |
            |               |   - AI extract CV profile |
            |               |   - AI assess fit         |
            |               |   - Map to numeric score  |
            |               |   - On failure: fall back |
            |               |     to heuristic          |
            |               +---------------------------+
            |                          |
            +-----------+--------------+
                        |
                        v
              +-------------------+
              | 7. Rank           |  Sort by score DESC, then name ASC for ties
              +-------------------+
                        |
                        v
              +-------------------------+
              | 8. Shortlist            |  Top N candidates whose score >= minimum threshold
              +-------------------------+
                        |
                        v
              +-------------------+
              | 9. Persist        |  Save batch, evaluations, eliminated candidates, token usage
              +-------------------+
                        |
                        v
                   Return results
```

---

## Heuristic Scoring (0-100)

All data is extracted using regex and dictionary lookups. No external API calls.

### What gets extracted

**From the job description** (`HeuristicJobDescriptionProfileFactory`):

| Field | How |
|---|---|
| Skills | Matched against a dictionary of 150+ known skills (tech, healthcare, finance, creative, etc.) |
| Required keywords | Tokens (>= 4 chars, not stop-words) from lines containing "required", "must", "qualifications", etc. Max 8. |
| Years of experience | Regex `\b(\d{1,2})\+?\s+years?\b` -- takes the highest number found |

**From each CV** (`HeuristicCandidateProfileFactory`):

| Field | How |
|---|---|
| Candidate name | Scans first 8 non-blank lines. Must be 3-50 chars, alphabetic, no digits/emails. Rejects common headings ("Curriculum Vitae", "Skills", etc.). Falls back to filename. |
| Skills | Same dictionary lookup as job description |
| Years of experience | Same regex as job description |

### Score calculation (`CandidateScoringService`)

Three components, summed:

#### 1. Skill Score (0-70 points)

```
matched = intersection(job skills, candidate skills)    [case-insensitive]

if job has no skills listed  -> 0 (or 30 if candidate has any skills)
otherwise                    -> (matched count / job skill count) * 70
```

A candidate who matches all required skills gets the full 70 points.

#### 2. Keyword Score (0-20 points)

```
candidate keywords = all tokens (>= 4 chars, not stop-words) from CV text
matched            = intersection(job required keywords, candidate keywords)

if no job keywords or no matches -> 0
otherwise                        -> (matched count / min(job keyword count, 10)) * 20
```

The denominator is capped at 10 to avoid penalising candidates when the job description has many keywords.

#### 3. Experience Score (0-10 points)

```
if required years is missing or candidate years is missing -> 0
otherwise -> min(candidate years / required years, 1.0) * 10
```

A candidate who meets or exceeds the required years gets 10. Below that, it's proportional.

#### Final score

```
total = skill + keyword + experience
total = clamp(total, 0, 100)
total = round to 1 decimal place
```

### Summary generation

The heuristic path builds a text summary listing:
- Matched skills
- Top 3 missing skills
- Matched keywords (up to 6, alphabetical)
- Estimated years of experience
- The deterministic score

---

## AI Scoring (0-100)

The AI path makes three LLM calls per screening run (one for the job, one extract + one assess per candidate).

### Step 1 -- AI job extraction

The LLM receives the job description and returns a structured `AiJobDescriptionProfile`:

- Role title, family, seniority level
- Essential requirements (with importance: MUST_HAVE, STRONG_PREFERENCE, NICE_TO_HAVE)
- Desirable requirements
- Tools/methods/systems
- Qualifications/certifications
- Domain context, soft skills, work conditions
- Extraction quality self-assessment (HIGH / MEDIUM / LOW)

### Step 2 -- AI candidate extraction (per CV)

The LLM receives the CV text and returns a structured `AiCandidateProfile`:

- Name, headline, seniority level
- Estimated years of relevant experience
- Demonstrated capabilities (each with evidence strength: STRONG / MODERATE / WEAK and supporting text)
- Tools, qualifications, domain experience, soft skills (all with evidence)
- Ambiguities or missing data

### Step 3 -- AI fit assessment (per CV)

The LLM receives both the AI job profile and AI candidate profile and returns an `AiFitAssessment`:

- **Overall recommendation**: STRONG_MATCH, POSSIBLE_MATCH, WEAK_MATCH, or NOT_RECOMMENDED
- **Confidence**: HIGH, MEDIUM, or LOW
- Dimension judgements: essential fit, desirable fit, experience fit, domain fit, credentials fit (each STRONG / PARTIAL / WEAK / NONE with rationale)
- Top strengths, top gaps, interview probe areas
- Recruiter-facing explanation

### Score mapping (`AiAssessmentToCandidateEvaluationMapper`)

The recommendation + confidence pair maps to a fixed numeric score:

| Recommendation | HIGH | MEDIUM | LOW |
|---|---|---|---|
| STRONG_MATCH | 90 | 85 | 80 |
| POSSIBLE_MATCH | 72 | 68 | 62 |
| WEAK_MATCH | 48 | 42 | 35 |
| NOT_RECOMMENDED | 20 | 15 | 10 |

The entire score is placed in the `skillScore` component. The `keywordScore` and `experienceScore` are set to 0 for AI-scored candidates.

### Fallback behaviour

If AI extraction or assessment fails for a specific candidate:
- That candidate is scored heuristically instead
- The scoring path is recorded as `"heuristic_fallback"`
- If any fallback happened, the batch mode is changed to `ai_with_fallbacks`

---

## Pre-filter / Reduction

When the number of readable CVs exceeds `analysisCap` (default 20):

1. Every CV is scored heuristically (quick, no API cost)
2. Candidates are sorted by heuristic score descending
3. The top `analysisCap` candidates proceed to full scoring
4. Eliminated candidates are stored with their pre-filter score and matched skills

In AI mode, the pre-filter also attempts to extract additional skills from the job description using the AI extractor, so the heuristic pre-filter uses a richer skill set.

---

## Ranking and Shortlisting

### Ranking (`RankingService`)

Candidates are sorted by:
1. Score descending (highest first)
2. Name ascending (alphabetical tie-breaker)

### Shortlisting (`ShortlistService`)

A candidate is shortlisted if **both** conditions are met:
- Their position in the ranking is within the requested shortlist count
- Their score meets or exceeds the minimum threshold

The threshold comes from the `ShortlistQuality` enum:

| Quality | Minimum score |
|---|---|
| EXCELLENT | 90 |
| VERY_GOOD | 75 |
| GOOD | 60 |
| ALL | 40 |

---

## Classes Involved

### Domain model

| Class | Location | Role |
|---|---|---|
| `ScoringMode` | `domain/` | Enum: `ai`, `heuristic`, `ai_with_fallbacks` |
| `ShortlistQuality` | `domain/` | Enum with score thresholds |
| `CandidateProfile` | `domain/` | Heuristic profile: name, skills, years |
| `JobDescriptionProfile` | `domain/` | Heuristic job profile: skills, keywords, years |
| `CandidateEvaluation` | `domain/` | Score + breakdown + summary + shortlist flag |
| `CandidateScoreBreakdown` | `domain/` | Skill / keyword / experience component scores |
| `ScreeningResult` | `domain/` | Job profile + list of evaluations |
| `ScreeningRunResult` | `domain/` | Full pipeline result including batch ID, stats, token usage |

### Heuristic scoring

| Class | Location | Role |
|---|---|---|
| `TextProfileHeuristicsService` | `service/` | Skill dictionary (150+), keyword extraction, years-of-experience regex |
| `HeuristicCandidateProfileFactory` | `service/` | Builds CandidateProfile from CV text |
| `HeuristicJobDescriptionProfileFactory` | `service/` | Builds JobDescriptionProfile from JD text |
| `CandidateScoringService` | `service/` | Calculates the 0-100 heuristic score (skill + keyword + experience) |

### AI scoring

| Class | Location | Role |
|---|---|---|
| `AiJobDescriptionProfile` | `ai/` | Structured AI-extracted job profile |
| `AiCandidateProfile` | `ai/` | Structured AI-extracted candidate profile |
| `AiFitAssessment` | `ai/` | AI judgement: recommendation, confidence, dimension fits |
| `RequirementItem` | `ai/` | A single job requirement with type and importance |
| `EvidenceItem` | `ai/` | A single candidate capability with evidence strength |
| `ExtractionQuality` | `ai/` | Enum: HIGH, MEDIUM, LOW |
| `MatchBand` | `ai/` | Enum: STRONG_MATCH through NOT_RECOMMENDED |
| `ConfidenceLevel` | `ai/` | Enum: HIGH, MEDIUM, LOW |
| `DimensionJudgement` | `ai/` | Level + rationale for one fit dimension |
| `AiAssessmentToCandidateEvaluationMapper` | `ai/` | Converts AI assessment to numeric score using the score matrix |
| `TokenUsage` | `ai/` | Prompt + completion token counts |
| `TokenUsageAccumulator` | `ai/` | Thread-safe token accumulator |

### Orchestration

| Class | Location | Role |
|---|---|---|
| `CandidateScreeningFacade` | `service/` | Main pipeline: extraction, dedup, pre-filter, scoring, ranking, persistence |
| `RankingService` | `service/` | Sorts evaluations by score desc, name asc |
| `ShortlistService` | `service/` | Marks top-N above-threshold candidates as shortlisted |
| `PipelineTimer` | `service/` | Tracks per-phase execution time |

### Document extraction

| Class | Location | Role |
|---|---|---|
| `ExtractedDocument` | `document/` | Filename + raw text from a CV |
| `DocumentExtractionOutcome` | `document/` | Success/failure wrapper for extraction |
| `UploadedCvValidationService` | `document/` | Validates uploaded files (size, type, readability) |

### Web layer

| Class | Location | Role |
|---|---|---|
| `ScreeningForm` | `web/` | Form binding: JD, files, shortlist count, quality, scoring mode |
| `ScreeningFormValidator` | `web/` | Validates word count, shortlist count, file validity |
| `HomeController` | `web/` | POST /analyse and /analyse/stream endpoints |

### Persistence

| Class | Location | Role |
|---|---|---|
| `ScreeningBatchEntity` | `persistence/` | Batch metadata: mode, stats, token usage, cost |
| `CandidateEvaluationEntity` | `persistence/` | Per-candidate: score, breakdown, AI profile JSON, rank |
| `EliminatedCandidateEntity` | `persistence/` | Pre-filter eliminations: score, matched skills |
| `ScreeningBatchRepository` | `persistence/` | JPA queries for batches and AI usage totals |
| `ScreeningHistoryService` | `persistence/` | Reads history, reconstructs results for the UI |

### Configuration

| Class | Location | Role |
|---|---|---|
| `RecruitmentProperties` | `config/` | `recruitment.*` properties: caps, thresholds, AI cost rates |

---

## Areas to Consider for Improvement

### Heuristic scoring

- **Skill dictionary is static.** The 150+ skills are hard-coded. New technologies or niche roles won't be recognised unless someone adds them to the list. A data-driven or configurable skill list could help.
- **Skill matching is exact (case-insensitive).** "React.js" and "ReactJS" are separate entries. Synonym/alias handling is basic.
- **Keyword extraction is simple tokenisation.** It splits on whitespace and filters by length/stop-words. Multi-word phrases like "machine learning" or "project management" are not matched as units.
- **Years of experience takes the highest number.** A CV that says "2 years of Java, 10 years in the industry" returns 10 regardless of which field the job asked about.
- **No weighting between essential and desirable skills.** All job skills carry equal weight in the 70-point skill bucket.
- **Score components are fixed weights (70/20/10).** These were chosen once and are not tuneable without code changes.

### AI scoring

- **Score is a coarse lookup table (12 values).** The entire 0-100 range collapses into 12 discrete scores. Two very different POSSIBLE_MATCH candidates both get 68 if confidence is MEDIUM.
- **No breakdown granularity.** The AI score is placed entirely in `skillScore`; `keywordScore` and `experienceScore` are 0. This means the UI score breakdown is meaningless for AI-scored candidates.
- **Three LLM calls per candidate.** One for job extraction (shared), one for CV extraction, one for fit assessment. The CV extraction and assessment could potentially be a single call.
- **No caching of AI job extraction.** If the same job description is submitted multiple times, the AI extracts it each time.
- **Fallback loses AI richness.** When AI fails for one candidate, that candidate's entire evaluation is purely heuristic with no partial AI data retained.

### Pipeline

- **Pre-filter always uses heuristic.** Even in AI mode, the reduction step uses heuristic scoring. A borderline candidate who would score well on AI assessment can be eliminated before AI ever sees them.
- **Deduplication is filename + text prefix only.** Two genuinely different CVs with the same first 500 characters would be treated as duplicates.
- **analysisCap is a hard cut-off.** Going from 21 to 20 candidates drops one entirely; there is no soft boundary or second-chance mechanism.