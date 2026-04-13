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
              |                             |  Scores ALL candidates heuristically, keeps top N,
              |                             |  then rescues borderline candidates within a configurable
              |                             |  margin. Stores eliminated candidates separately.
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
            |               |     using dimension       |
            |               |     judgements             |
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

All data is extracted using regex, dictionary lookups, and synonym aliases. No external API calls.

### What gets extracted

**From the job description** (`HeuristicJobDescriptionProfileFactory`):

| Field | How |
|---|---|
| Skills | Matched against a dictionary of 150+ known skills (tech, healthcare, finance, creative, etc.) plus 35+ synonym aliases |
| Required keywords | Tokens (>= 4 chars, not stop-words) from lines containing "required", "must", "qualifications", etc. Max 8. |
| Years of experience | Regex `\b(\d{1,2})\+?\s+years?\b` -- takes the highest number found |

**From each CV** (`HeuristicCandidateProfileFactory`):

| Field | How |
|---|---|
| Candidate name | Scans first 8 non-blank lines. Must be 3-50 chars, alphabetic, no digits/emails. Rejects common headings ("Curriculum Vitae", "Skills", etc.). Falls back to filename. |
| Skills | Same dictionary + alias lookup as job description |
| Years of experience | Same regex as job description |

### Sector skill boost (heuristic mode)

When a sector is selected, `SectorSkillDictionary.getSkills(sector)` returns a list of sector-specific terms that supplement the generic dictionary. These are injected as `additionalSkills` into `TextProfileHeuristicsService.extractSkills()` for **both** the job description and each candidate CV, so sector vocabulary is matched consistently on both sides.

This happens in two places inside `CandidateScreeningFacade`:

1. **Job profile** — immediately after `HeuristicJobDescriptionProfileFactory.create()`. If any sector terms appear in the JD text, they are added to the job's extracted skill list.
2. **Candidate profile** — for every CV evaluated heuristically. `buildSectorAwareCandidateProfile()` re-runs skill extraction with the same sector term list, so skills like "NMC" or "SMSTS" found in a CV are matched against the job's requirements.

The sector boost only adds a term if it actually appears in the text — no noise is injected for terms not present. If the sector is `GENERIC`, the skill list is empty and behaviour is identical to the pre-sector baseline.

Sector skill coverage per sector (selected highlights):

| Sector | Examples of added terms |
|---|---|
| IT & Technology | C#, .NET, Go, GraphQL, Redis, Kafka, GitHub Actions, TDD, System Design |
| Healthcare | NMC, GMC, HCPC, CQC, Venepuncture, Palliative Care, MDT, Enhanced DBS |
| Finance | ACA, ACCA, CFA, IFRS, Bloomberg Terminal, Financial Modelling, AML, KYC |
| Education | QTS, PGCE, EYFS, KS1–KS4, SEND, EHCP, Ofsted, Behaviour Management |
| Sales & Marketing | HubSpot, Google Analytics, Lead Generation, ABM, ARR, Cold Calling |
| Manual / Labour | IPAF, PASMA, NPORS, HGV, Asbestos Awareness, Working at Height, COSHH |
| Retail | Merchandising, EPOS, Challenge 25, Planogram, Loss Prevention, Visual Merchandising |
| Construction | SMSTS, SSSTS, JCT, NEC, Quantity Surveying, Bill of Quantities, RICS |
| Manufacturing | Lean Manufacturing, Six Sigma, OEE, 5S, ISO 9001, FMEA, Kaizen, MRP |

### Skill alias resolution

The `SKILL_ALIASES` map contains 35+ normalised alias phrases that map to canonical skill keys. When extracting skills, after the direct KNOWN_SKILLS lookup, each alias is checked against the text:

- Aliases of 4+ characters use simple substring matching (same as direct skills)
- Aliases of 3 characters or fewer (e.g. "ui", "ux", "k8s") use word-boundary matching via `containsToken()` to prevent false positives inside longer words (e.g. "ui" inside "liquid")

Sample aliases:

| Alias | Canonical Skill |
|---|---|
| `reactjs`, `react js` | React |
| `nodejs` | Node.js |
| `postgres`, `psql` | PostgreSQL |
| `amazon web services` | AWS |
| `k8s`, `kube` | Kubernetes |
| `google cloud`, `google cloud platform` | GCP |
| `restful`, `rest apis`, `restful api` | REST APIs |
| `springboot` | Spring Boot |
| `ms office`, `office 365`, `microsoft 365` | Microsoft Office |
| `ux`, `user experience`, `user experience design` | UX Design |
| `ui`, `user interface`, `user interface design` | UI Design |
| `adobe photoshop` | Photoshop |
| `programme management` | Project Management |
| `full driving licence`, `clean driving licence` | Driving Licence |

### Requirement classification

Before scoring, `classifyRequirements()` scans the job description line by line to separate skills into three buckets:

**Essential indicators** — lines containing: "must", "required", "essential", "mandatory", "minimum", "need", "critical", "necessary", "key requirement"

**Desirable indicators** — lines containing: "desirable", "nice to have", "preferred", "bonus", "advantageous", "ideally", "would be beneficial", "plus"

For each extracted skill:
- Appears on an essential line only -> **essential**
- Appears on a desirable line only -> **desirable**
- Appears on both, or neither -> **unclassified**

If no essential or desirable lines are detected at all, every skill goes to **unclassified**. This preserves backward-compatible behaviour — the scoring method redistributes weight accordingly.

### Score calculation (`CandidateScoringService`)

Five components, summed to a maximum of 100:

#### 1. Essential Requirement Fit (0-40 points)

```
matched = intersection(essential skills, candidate skills)

if no essential skills detected -> 0 (points redistributed to broader fit)
otherwise                       -> (matched count / total essential count) * 40
```

#### 2. Broader Skill Fit (0-25, or 0-65 if no essentials detected)

```
matched = intersection(all job skills, candidate skills)

if essentials exist  -> max = 25
if no essentials     -> max = 65  (absorbs the 40 points from essential fit)

if job has no skills -> half of max if candidate has any skills, else 0
otherwise            -> (matched count / total job skill count) * max
```

This intentionally overlaps with essential scoring — a candidate matching all essentials earns points in both buckets.

#### 3. Keyword Support (0-15 points)

```
candidate keywords = all tokens (>= 4 chars, not stop-words) from CV text
matched            = intersection(job required keywords, candidate keywords)

if no job keywords or no matches -> 0
otherwise                        -> (matched count / min(job keyword count, 10)) * 15
```

#### 4. Experience Fit (0-10 points)

```
if required years is missing or candidate years is missing -> 0
otherwise -> min(candidate years / required years, 1.0) * 10
```

#### 5. Gap Penalty (0 to -10 points)

Applied only when essential skills are detected:

| Condition | Penalty |
|---|---|
| Missing ALL essential skills (and at least 2 exist) | -10 |
| Missing more than half of essential skills | -5 |
| Otherwise | 0 |

#### Final score

```
skillScore = essential fit + broader skill fit + gap penalty  (clamped to 0-100)
total      = skillScore + keyword support + experience fit
total      = clamp(total, 0, 100)
total      = round to 1 decimal place
```

The components are stored in `CandidateScoreBreakdown` as:
- `skillScore` = essential fit + broader skill fit + gap penalty
- `keywordScore` = keyword support
- `experienceScore` = experience fit

### Summary generation

The heuristic path builds a text summary containing:
- Essential requirements met (e.g. "3/5") if any essentials were detected
- Matched skills
- Top 3 missing skills, prioritising essential skills first
- Matched keywords (up to 6, alphabetical)
- Estimated years of experience
- The deterministic score

---

## AI Scoring (0-100)

The AI path makes three LLM calls per screening run (one for the job, one extract + one assess per candidate).

### Step 1 — AI job extraction

The LLM receives the job description and returns a structured `AiJobDescriptionProfile`:

- Role title, family, seniority level
- Essential requirements (with importance: MUST_HAVE, STRONG_PREFERENCE, NICE_TO_HAVE)
- Desirable requirements
- Tools/methods/systems
- Qualifications/certifications
- Domain context, soft skills, work conditions
- Extraction quality self-assessment (HIGH / MEDIUM / LOW)

### Step 2 — AI candidate extraction (per CV)

The LLM receives the CV text and returns a structured `AiCandidateProfile`:

- Name, headline, seniority level
- Estimated years of relevant experience
- Demonstrated capabilities (each with evidence strength: STRONG / MODERATE / WEAK and supporting text)
- Tools, qualifications, domain experience, soft skills (all with evidence)
- Ambiguities or missing data

### Step 3 — AI fit assessment (per CV)

The LLM receives both the AI job profile and AI candidate profile, along with a **sector-specific system prompt**, and returns an `AiFitAssessment`.

The system prompt is resolved once per batch by `PromptProviderFactory`:
- If the recruiter selected a sector in the UI → that sector's prompt is used
- If blank → falls back to `recruitment.default-sector` config value
- If unknown or unset → `generic.txt` is used

Prompt files live in `src/main/resources/prompts/` and are loaded lazily and cached by `PromptLoaderService`. Each file shares the same core output rules but adds a **sector-specific guidance block** that emphasises the signals most relevant to that industry:

| Sector | Key emphases |
|---|---|
| Generic | Cross-domain, evidence-based, no sector bias |
| IT & Technology | Depth over breadth, delivery evidence, CI/CD, cloud, production systems |
| Healthcare | Mandatory registrations (NMC/GMC/HCPC), clinical settings, compliance, patient-context soft skills |
| Finance | Professional qualifications (ACA/CFA/CIMA), quantified outcomes, regulatory knowledge, financial tools |
| Education | QTS/DBS requirements, subject specialism, key stage alignment, safeguarding, pastoral evidence |
| Sales & Marketing | Quantified commercial outcomes, channel alignment, CRM tools, B2B/B2C context |
| Manual / Labour | CSCS/forklift/trade licences, employment stability, physical work context, shift flexibility |
| Retail | Customer-facing experience, sales KPIs, stock/ops, compliance (Challenge 25, food hygiene) |
| Construction | CSCS, SMSTS/SSSTS, project type and value, H&S, programme management |
| Manufacturing | Trade qualifications, CI frameworks (Lean/Six Sigma), OEE metrics, shift patterns, ERP systems |

The `AiFitAssessment` returned contains:

- **Overall recommendation**: STRONG_MATCH, POSSIBLE_MATCH, WEAK_MATCH, or NOT_RECOMMENDED
- **Confidence**: HIGH, MEDIUM, or LOW
- **Five dimension judgements** (each with level and rationale):
  - `essentialFit` — how well the candidate meets essential requirements
  - `desirableFit` — how well the candidate meets desirable requirements
  - `experienceFit` — how well the candidate's experience matches
  - `domainFit` — how relevant the candidate's domain background is
  - `credentialsFit` — how well the candidate's qualifications match
- Top strengths, top gaps, interview probe areas
- Recruiter-facing explanation

### Score calculation (`AiAssessmentToCandidateEvaluationMapper`)

The score is calculated from the five dimension judgements using weighted continuous scoring.

#### Dimension level values

| JudgementLevel | Numeric value |
|---|---|
| STRONG | 4 |
| PARTIAL | 3 |
| WEAK | 2 |
| NONE | 1 |

If a dimension judgement is null, it defaults to 2 (WEAK equivalent).

#### Dimension weights

| Dimension | Weight |
|---|---|
| essentialFit | 0.35 |
| experienceFit | 0.25 |
| desirableFit | 0.15 |
| domainFit | 0.15 |
| credentialsFit | 0.10 |

#### Calculation

```
weightedSum = (essentialLevel * 0.35) + (experienceLevel * 0.25)
            + (desirableLevel * 0.15) + (domainLevel * 0.15)
            + (credentialsLevel * 0.10)

// weightedSum ranges from 1.0 (all NONE) to 4.0 (all STRONG)
baseScore = ((weightedSum - 1.0) / 3.0) * 100
```

#### Confidence modifier

| Confidence | Multiplier |
|---|---|
| HIGH | 1.00 |
| MEDIUM | 0.95 |
| LOW | 0.85 |

```
finalScore = clamp(baseScore * confidenceMultiplier, 0, 100)
```

#### Score examples

| Dimensions | Confidence | Score |
|---|---|---|
| All STRONG | HIGH | 100.0 |
| All STRONG | MEDIUM | 95.0 |
| All PARTIAL | MEDIUM | 63.3 |
| All WEAK | LOW | 28.3 |
| All NONE | HIGH | 0.0 |
| STRONG essential, WEAK experience, PARTIAL desirable, PARTIAL domain, NONE credentials | MEDIUM | 60.2 |

#### Score breakdown distribution

The AI score is distributed into the three `CandidateScoreBreakdown` fields so the UI displays meaningful component values:

| Component | Share |
|---|---|
| `skillScore` (essential + desirable) | 50% of finalScore |
| `experienceScore` | 25% of finalScore |
| `keywordScore` (domain + credentials) | remainder (ensures exact sum) |

#### Legacy fallback

If ALL five dimension judgements are null (older AI response format), the mapper falls back to a 12-value lookup table using `overallRecommendation` x `confidence`:

| Recommendation | HIGH | MEDIUM | LOW |
|---|---|---|---|
| STRONG_MATCH | 90 | 85 | 80 |
| POSSIBLE_MATCH | 72 | 68 | 62 |
| WEAK_MATCH | 48 | 42 | 35 |
| NOT_RECOMMENDED | 20 | 15 | 10 |

### Fallback behaviour

If AI extraction or assessment fails for a specific candidate:
- That candidate is scored heuristically instead
- The scoring path is recorded as `"heuristic_fallback"`
- If any fallback happened, the batch mode is changed to `ai_with_fallbacks`

---

## Pre-filter / Reduction

When the number of readable CVs exceeds `analysisCap` (default 20), the pre-filter decides which candidates proceed to full scoring. This uses a margin-based rescue system rather than a hard cut-off.

### How it works

1. **Score all readable CVs** heuristically (quick, no API cost) and sort by score descending.

2. **Select guaranteed group**: the top `analysisCap` candidates always proceed.

3. **Calculate the rescue floor**: the score of the last guaranteed candidate minus `prefilterBorderlineMargin` (default 10 points), with a minimum of 0.

4. **Rescue borderline candidates** from the remaining pool, up to `prefilterMaxRescue` (default 8):

   | Rescue rule | Condition |
   |---|---|
   | **Margin rescue** | Candidate's score >= rescue floor |
   | **Skill + experience rescue** | Candidate matched >= 50% of all job skills AND has years-of-experience evidence in their CV |

5. **Combine** guaranteed + rescued candidates as the analysis group. Everyone else becomes eliminated.

### Configuration

| Property | Default | Effect |
|---|---|---|
| `recruitment.analysis-cap` | 20 | Maximum guaranteed candidates |
| `recruitment.prefilter-borderline-margin` | 10.0 | Points below cutoff that still qualify for rescue |
| `recruitment.prefilter-max-rescue` | 8 | Maximum candidates rescued per run |

### Backward compatibility

Setting `prefilterBorderlineMargin` to 0 disables rescue entirely, producing the same hard cut-off as before.

### AI mode enhancement

In AI mode, the pre-filter also attempts to extract additional skills from the job description using the AI skill extractor, so the heuristic pre-filter uses a richer skill set when deciding who proceeds.

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
| `TextProfileHeuristicsService` | `service/` | Skill dictionary (190+), alias map (35+), requirement classification, keyword extraction, years-of-experience regex |
| `SectorSkillDictionary` | `ai/` | Static map of `Sector → List<String>` of sector-specific skill terms. Injected as `additionalSkills` into both job and candidate skill extraction in heuristic mode. Returns empty list for GENERIC. |
| `RequirementClassification` | `service/` | Record: essentialSkills, desirableSkills, unclassifiedSkills |
| `HeuristicCandidateProfileFactory` | `service/` | Builds CandidateProfile from CV text |
| `HeuristicJobDescriptionProfileFactory` | `service/` | Builds JobDescriptionProfile from JD text |
| `CandidateScoringService` | `service/` | Calculates the 0-100 weighted heuristic score (essential fit + broader skill fit + keyword support + experience fit + gap penalty) |

### AI scoring

| Class | Location | Role |
|---|---|---|
| `AiJobDescriptionProfile` | `ai/` | Structured AI-extracted job profile |
| `AiCandidateProfile` | `ai/` | Structured AI-extracted candidate profile |
| `AiFitAssessment` | `ai/` | AI judgement: recommendation, confidence, five dimension fits |
| `DimensionJudgement` | `ai/` | Level (STRONG/PARTIAL/WEAK/NONE) + rationale for one fit dimension |
| `JudgementLevel` | `ai/` | Enum: STRONG, PARTIAL, WEAK, NONE |
| `ConfidenceLevel` | `ai/` | Enum: HIGH, MEDIUM, LOW |
| `MatchBand` | `ai/` | Enum: STRONG_MATCH through NOT_RECOMMENDED |
| `RequirementItem` | `ai/` | A single job requirement with type and importance |
| `EvidenceItem` | `ai/` | A single candidate capability with evidence strength |
| `ExtractionQuality` | `ai/` | Enum: HIGH, MEDIUM, LOW |
| `AiAssessmentToCandidateEvaluationMapper` | `ai/` | Converts AI dimension judgements to weighted continuous score (0-100) with confidence modifier. Falls back to legacy 12-value lookup when dimensions are absent. |
| `TokenUsage` | `ai/` | Prompt + completion token counts |
| `TokenUsageAccumulator` | `ai/` | Thread-safe token accumulator |
| `Sector` | `ai/` | Enum of supported industry sectors (GENERIC, IT_AND_TECHNOLOGY, HEALTHCARE, FINANCE, EDUCATION, SALES_AND_MARKETING, MANUAL_LABOUR, RETAIL, CONSTRUCTION, MANUFACTURING). Each carries a display label and a prompt file key. `fromString()` resolves UI/config values with fallback to GENERIC. |
| `PromptProvider` | `ai/` | Strategy interface: `getSystemPrompt()` returns the fit-assessor system prompt for the resolved sector; `getSector()` identifies which sector was resolved. |
| `PromptLoaderService` | `ai/` | Loads fit-assessor prompts from `classpath:prompts/<key>.txt`. Results are cached in memory after first load. Falls back to `generic.txt` if a sector file is missing; throws on startup if `generic.txt` itself is absent. |
| `PromptProviderFactory` | `ai/` | Resolves a `Sector` to a `PromptProvider` by delegating to `PromptLoaderService`. Null sector always produces the GENERIC provider. |

### Orchestration

| Class | Location | Role |
|---|---|---|
| `CandidateScreeningFacade` | `service/` | Main pipeline: extraction, dedup, margin-based pre-filter with rescue, scoring, ranking, persistence |
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
| `ScreeningForm` | `web/` | Form binding: JD, files, shortlist count, quality, scoring mode, sector |
| `ScreeningFormValidator` | `web/` | Validates word count, shortlist count, file validity |
| `HomeController` | `web/` | POST /analyse and /analyse/stream endpoints; passes sector to facade |

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
| `RecruitmentProperties` | `config/` | `recruitment.*` properties: caps, thresholds, AI cost rates, pre-filter margin and rescue limit, `defaultSector` (resolved via `getEffectiveSector()`) |

---

## Areas to Consider for Improvement

### Heuristic scoring

- **Skill dictionary is static.** The 190+ generic skills are hard-coded. New technologies or niche roles won't be recognised unless someone adds them. Sector dictionaries in `SectorSkillDictionary` extend coverage per sector, but those are also static. A data-driven or configurable skill list could help.
- **Years of experience takes the highest number.** A CV that says "2 years of Java, 10 years in the industry" returns 10 regardless of which field the job asked about.
- **Score component weights are fixed.** The 40/25/15/10 split and the gap penalties are not tuneable without code changes.
- **Requirement classification is line-based.** A skill mentioned in a paragraph that also contains "must have" will be classified as essential, even if the "must have" referred to a different skill in the same sentence.

### AI scoring

- **Three LLM calls per candidate.** One for job extraction (shared), one for CV extraction, one for fit assessment. The CV extraction and assessment could potentially be a single call.
- **No caching of AI job extraction.** If the same job description is submitted multiple times, the AI extracts it each time.
- **Fallback loses AI richness.** When AI fails for one candidate, that candidate's entire evaluation is purely heuristic with no partial AI data retained.
- **Dimension weights are fixed.** The 0.35/0.25/0.15/0.15/0.10 weights in `AiAssessmentToCandidateEvaluationMapper` cannot be tuned per role type. Sector-specific system prompts partially address this by steering the LLM's judgements (e.g. the Healthcare prompt instructs the model to treat missing certifications as hard gaps), but the Java-side weighting remains constant across all sectors.
- **Sector prompt applies only to fit assessment.** The job description and CV extractor prompts are currently generic regardless of sector. Adding sector awareness to the extraction stage could improve what signals are captured in the structured profiles.

### Pipeline

- **Pre-filter always uses heuristic.** Even in AI mode, the reduction step uses heuristic scoring. A borderline candidate who would score well on AI assessment can be eliminated before AI ever sees them. The margin-based rescue mitigates this but doesn't eliminate it.
- **Deduplication is filename + text prefix only.** Two genuinely different CVs with the same first 500 characters would be treated as duplicates.