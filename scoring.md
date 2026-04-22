# How the Scoring System Works

This document explains the scoring engine that powers every candidate evaluation — what it measures, why it's designed that way, and what sets it apart. Written for prospective clients and stakeholders.

---

## The Core Philosophy

Most screening tools give you a number and ask you to trust it. This system is different: **every score is explainable, reproducible, and grounded in specific evidence from the job description and the CV**.

There are no mystery algorithms. Every point awarded can be traced back to a matched skill, a met keyword, a verified year of experience, or an AI judgement with a written rationale. Recruiters never see the raw number — they see human-readable labels (**Excellent / Strong / Good / Partial / Weak**) backed by a breakdown they can interrogate.

---

## Two Engines, One Standard

The platform offers two scoring modes. Both produce a 0-100 score on the same scale, with the same output labels and the same shortlisting logic. The difference is in how evidence is gathered.

| Mode | Best for | What it does |
|---|---|---|
| **Heuristic** | Speed, cost-zero screening, structured JDs | Deterministic rule-based analysis. No API calls, results in seconds. |
| **AI** | Complex roles, unstructured JDs, high-stakes shortlists | GPT-4o extracts structured profiles and judges fit across five dimensions. Falls back to heuristic per-candidate if AI fails. |

Clients can mix and match depending on volume, urgency, and role complexity. A recruiter processing 200 warehouse CVs on a Friday afternoon uses Heuristic. A talent partner filling a VP of Engineering role uses AI. Same interface, same output format.

---

## The Heuristic Engine

### What it reads

The system extracts three things from both the job description and every CV:

- **Skills** — matched against a dictionary of 190+ recognised skills across technology, healthcare, finance, education, construction, manufacturing, and more. Industry-standard aliases are handled automatically (`k8s` → Kubernetes, `NMC` → NMC registration, `programme management` → Project Management).
- **Keywords** — up to 8 job-critical terms found on lines containing requirements language ("must", "essential", "required", "mandatory").
- **Years of experience** — detected from numeric patterns in both the JD and CV text.

### How it classifies requirements

Before scoring, the system reads the job description and classifies every required skill into one of three buckets:

- **Essential** — appears on a line containing "must", "required", "essential", "mandatory", "critical"
- **Desirable** — appears on a line containing "desirable", "preferred", "nice to have", "advantageous"
- **Unclassified** — not explicitly signalled either way

This matters because the score weights essential requirements much more heavily. A candidate who misses a desirable skill is penalised less than one who misses a must-have.

### The score breakdown

Five components combine to produce the final score (max 100):

| Component | Max points | What it measures |
|---|---|---|
| **Essential Requirement Fit** | 40 | Percentage of essential skills matched |
| **Broader Skill Fit** | 25 (or 65 if no essentials) | Percentage of all job skills matched |
| **Keyword Support** | 15 | Job-critical terms found in the CV |
| **Experience Fit** | 10 | Candidate years vs. required years (proportional) |
| **Gap Penalty** | −5 to −10 | Applied when essential skills are missing at scale |

**If the candidate is missing every single essential requirement** (and at least two exist), a −10 penalty is applied. Missing more than half earns a −5 penalty. This ensures that candidates who look good on breadth but fail on fundamentals are correctly ranked lower.

All weights are configurable without code changes and can be tuned per deployment.

### Why this works for high-volume screening

- **Zero API cost.** No external calls means no latency, no rate limits, no per-CV spend.
- **Deterministic.** The same CV and JD will always produce the same score. Nothing drifts.
- **Transparent.** The summary shown to the recruiter lists exactly which skills were matched, which were missing, and what the experience comparison looked like.
- **Sector-aware.** Selecting a sector (Healthcare, Construction, IT, etc.) injects a curated list of industry-specific terms into both sides of the match. A healthcare JD mentioning "NMC" and a CV mentioning "NMC" will score that as a skill match. Without sector injection, generic dictionaries miss this.

---

## The AI Engine

### What the AI does

The AI path makes three structured LLM calls per screening run.

**Step 1 — Job Description Extraction (once per batch)**

The AI reads the job description and returns a structured profile: role title, seniority, a classified list of essential and desirable requirements (each with an importance level: MUST_HAVE, STRONG_PREFERENCE, NICE_TO_HAVE), tools and systems, qualifications, domain context, and soft skills. The AI also rates its own confidence in the extraction quality.

**Step 2 — CV Extraction (once per candidate, run in parallel)**

The AI reads each CV and returns a structured candidate profile: demonstrated capabilities (each with an evidence strength rating: STRONG for concrete metrics, MODERATE for contextual evidence, WEAK for keyword-only claims), tools, qualifications, domain experience, and an estimated years-of-experience figure inferred from employment dates.

This is a key differentiator: **the AI distinguishes between a candidate who says they "have experience with Agile" and one who ran a 12-person Scrum team for three years**. Only the second gets a STRONG evidence rating.

**Step 3 — Fit Assessment (once per candidate, parallel)**

The AI receives both structured profiles and a sector-specific system prompt. It returns five independent dimension judgements, each rated STRONG / PARTIAL / WEAK / NONE with a written rationale:

| Dimension | What it assesses |
|---|---|
| **Essential Fit** | Does the candidate demonstrably meet the must-haves? |
| **Experience Fit** | Is their experience level and relevance a genuine match? |
| **Desirable Fit** | How many of the nice-to-haves do they bring? |
| **Domain Fit** | Does their industry/sector background align? |
| **Credentials Fit** | Are the required qualifications, licences, or certifications present? |

The AI also returns the recruiter-facing explanation, top strengths, top gaps, and suggested interview probe areas — all surfaced directly in the results UI.

### Why the AI never produces a raw number

The AI is instructed — explicitly, in every prompt — **not to produce a free-form numeric score**. This is a deliberate architectural choice.

LLM-generated numbers are inconsistent. The same candidate evaluated twice may receive 73 one time and 68 the next, with no explanation for the difference. By having the AI produce bounded categorical judgements (STRONG / PARTIAL / WEAK / NONE), and then mapping those to a numeric score in deterministic Java code, the system gets the best of both worlds: **AI intelligence for understanding context and evidence, deterministic mathematics for consistency and auditability**.

### How the AI score is calculated

The five dimension judgements are converted to numeric values (STRONG=4, PARTIAL=3, WEAK=2, NONE=1) and combined using weighted averaging:

| Dimension | Default Weight |
|---|---|
| Essential Fit | 35% |
| Experience Fit | 25% |
| Desirable Fit | 15% |
| Domain Fit | 15% |
| Credentials Fit | 10% |

The weighted sum (which ranges from 1.0 to 4.0) is normalised to a 0-100 scale, then adjusted by a confidence multiplier (HIGH=1.00, MEDIUM=0.95, LOW=0.85). This means a HIGH-confidence strong assessment scores higher than a LOW-confidence one, even if the dimension judgements are identical.

**These weights are fully configurable and can be overridden per industry sector.** Healthcare deployments, for example, can weight Credentials Fit at 25% (to reflect that NMC/GMC registration is non-negotiable) while reducing Experience Fit proportionally.

### Sector-specific AI intelligence

Each sector uses a tailored system prompt that instructs the AI on what signals matter most:

| Sector | Key focus areas |
|---|---|
| **IT & Technology** | Production delivery evidence; depth over buzzword breadth; CI/CD and cloud architecture; penalises CVs full of keywords with no shipped product evidence |
| **Healthcare** | Mandatory registrations (NMC, GMC, HCPC) are treated as hard requirements — a STRONG_MATCH is impossible without them; clinical setting context; safeguarding awareness |
| **Finance** | Professional qualifications (ACA, CFA, CIMA); quantified outcomes; regulatory knowledge (FCA, AML, KYC, IFRS) |
| **Education** | QTS and DBS are hard requirements; key stage alignment; subject specialism; safeguarding and pastoral evidence |
| **Construction** | CSCS, SMSTS/SSSTS; project type and value; H&S compliance; programme management evidence |
| **Manufacturing** | Continuous improvement frameworks (Lean, Six Sigma); OEE and shift pattern alignment; ERP system experience |
| **Sales & Marketing** | Quantified commercial outcomes (ARR, pipeline growth); channel and tool alignment; B2B/B2C context |

This means the system does not apply a generic "finance person" or "developer" lens — it applies the specific evaluative criteria that experienced sector recruiters use.

---

## Efficiency at Scale: The Pre-Filter

When a batch contains more CVs than the selected analysis depth allows, the system does not randomly discard candidates. Instead, it applies a **margin-based rescue pre-filter**:

1. All readable CVs are scored quickly with the heuristic engine.
2. The top N candidates (based on the selected screening depth) are guaranteed to proceed to full analysis.
3. Candidates who fall just outside the cutoff — within 10 points of the last guaranteed candidate, or who matched at least 50% of job skills with verifiable experience — are **rescued** and added to the analysis group.
4. Everyone else is logged as eliminated with their pre-filter score stored for audit.

This means strong candidates near the borderline are not unfairly cut off by a hard threshold. The rescue logic exists precisely to catch the candidate who looks slightly weaker on breadth but has genuine core-skill alignment.

**Screening depth options:**

| Option | Candidates analysed |
|---|---|
| Fast | 10 |
| Balanced (default) | 20 |
| Thorough | 50 |

---

## Deduplication

Before any scoring begins, the system removes duplicate CVs automatically:

- **Exact duplicates** are detected via cryptographic hashing of the normalised CV text. If two files produce the same hash, only the first is kept.
- **Near-duplicates** (e.g. a slightly reformatted version of the same CV) are detected using Jaccard word similarity. If two files attributed to the same candidate are 85% or more similar, the shorter file is dropped and the longer one is kept.

This prevents the same candidate from inflating your shortlist count.

---

## From Score to Shortlist

The numeric score is never shown to the recruiter. It maps to display labels:

| Score range | Label | Meaning |
|---|---|---|
| 90 – 100 | **Excellent match** | Meets virtually all requirements with strong evidence |
| 75 – 89 | **Strong match** | Meets core requirements; minor gaps only |
| 60 – 74 | **Good match** | Solid candidate; some notable gaps |
| 40 – 59 | **Partial match** | Relevant background but meaningful gaps |
| Below 40 | **Weak match** | Insufficient alignment for this role |

The recruiter sets a **shortlist quality threshold** before the run:

| Setting | Minimum score to be shortlisted |
|---|---|
| Excellent only | 90 |
| Very Good (default) | 75 |
| Good | 60 |
| Show all | 40 |

A candidate can receive a "Good match" label without being shortlisted if the threshold is set to "Very Good". Labels and shortlist status are independent, so the recruiter sees the full picture rather than a binary pass/fail.

---

## What Makes This Approach Optimal

**1. Consistency you can defend.** Every score is a deterministic function of extracted data and configured weights. You can re-run the same batch and get the same result. There are no random elements.

**2. Transparency at every layer.** Recruiters see which skills matched, which were missing, and — in AI mode — a written rationale for every dimension judgement. Nothing is hidden behind a "trust the algorithm" wall.

**3. AI intelligence without AI unpredictability.** The LLM provides contextual understanding and evidence quality assessment. The numeric score is calculated in code. This combination is more reliable than asking an LLM to "score this candidate out of 100".

**4. Sector depth.** Generic screening tools apply one set of rules to every industry. This system applies sector-specific scoring weights, skill dictionaries, and AI assessment criteria. The difference between screening a nurse and a software engineer is not cosmetic — it's fundamental.

**5. Graceful degradation.** If AI is unavailable or fails for a specific candidate, the system falls back to heuristic scoring automatically. No batch fails silently. The scoring path used for every candidate is recorded.

**6. Configurable without code changes.** Every scoring weight — heuristic component maximums, AI dimension weights, sector-specific weight overrides, pre-filter thresholds — is configurable via application properties. Clients can tune the system to match their internal hiring frameworks without a development cycle.

**7. Cost visibility.** Every AI screening run records the token usage and estimated cost. Clients always know what AI analysis cost them and can compare results across scoring modes.

---

## Summary

| Capability | Heuristic | AI |
|---|---|---|
| Cost per run | Zero (no API) | Token-based (gpt-4o-mini) |
| Speed | Seconds | 15–60 seconds depending on batch size |
| Sector awareness | Skill dictionary injection | Custom system prompts + weight overrides |
| Evidence quality assessment | No | Yes (STRONG / MODERATE / WEAK per capability) |
| Requirement importance grading | Yes (Essential / Desirable) | Yes (MUST_HAVE / STRONG_PREFERENCE / NICE_TO_HAVE) |
| Rationale per candidate | Skill match list + gaps | Written dimension rationales + strengths + gaps + interview probes |
| Reproducibility | 100% deterministic | Deterministic scoring; LLM extraction may vary slightly |
| Fallback on failure | N/A (no external calls) | Automatically falls back to heuristic per candidate |
