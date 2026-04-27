# Production Readiness Refactor

This document records the refactor changes made to prepare the application for higher concurrent usage while preserving existing user-facing functionality.

## Summary

The refactor focused on operational safety rather than product behavior. Screening flow, scoring rules, AI prompts, templates, and persisted result structure were left unchanged. The changes add configurable concurrency limits, move ad-hoc background work onto managed infrastructure, increase resilience of temporary in-memory stores, and add production profile defaults for observability, graceful shutdown, and PostgreSQL deployment.

## Changes Made

### Added bounded screening concurrency

Files changed:

- `src/main/java/com/recruiter/service/CandidateScreeningFacade.java`
- `src/main/java/com/recruiter/config/RecruitmentProperties.java`
- `src/main/resources/application.yml`

What changed:

- Added a fair `Semaphore` around full screening runs.
- Added a separate fair `Semaphore` around concurrent AI candidate evaluations.
- Added configuration properties:
  - `recruitment.concurrency.max-concurrent-screening-runs`
  - `recruitment.concurrency.max-concurrent-ai-candidate-evaluations`
- Added default values in `application.yml`:
  - `max-concurrent-screening-runs: 25`
  - `max-concurrent-ai-candidate-evaluations: 16`

Why:

The previous implementation used virtual threads and `CompletableFuture` fanout without any application-level backpressure. Virtual threads are cheap, but external AI calls, DB writes, file parsing, memory, and downstream rate limits are not unlimited. With hundreds of users, unbounded screening requests could overload the JVM, database pool, AI provider, or host network.

This keeps the same screening result logic while making concurrency explicit and tunable per environment.

### Moved SSE background work onto the managed executor

Files changed:

- `src/main/java/com/recruiter/web/HomeController.java`

What changed:

- Replaced direct `Thread.startVirtualThread(...)` calls with `screeningVirtualExecutor.submit(...)`.
- The same executor is now used for streaming screening work and immediate validation/error SSE responses.

Why:

Direct thread creation bypasses Spring-managed lifecycle and makes shutdown behavior harder to reason about. Using the managed executor keeps background work under the same application-managed resource boundary as candidate screening.

### Made temporary rerun and JD review stores configurable

Files changed:

- `src/main/java/com/recruiter/web/RerunStore.java`
- `src/main/java/com/recruiter/web/JdReviewStore.java`
- `src/main/java/com/recruiter/config/RecruitmentProperties.java`
- `src/main/resources/application.yml`

What changed:

- Replaced hardcoded tiny store limits with configuration:
  - `recruitment.transient-stores.rerun-max-entries`
  - `recruitment.transient-stores.jd-review-max-entries`
- Increased defaults to 250 entries each.
- Changed the underlying `LinkedHashMap` access order to LRU-style eviction.

Why:

The previous hardcoded limits of 10 rerun entries and 20 JD review entries were too small for concurrent usage. With many users, valid recent rerun/review state could be evicted quickly. The stores remain in-memory and temporary, but they are now more appropriate for multi-user usage and can be tuned without code changes.

### Added production-oriented Spring configuration

Files changed:

- `src/main/resources/application.yml`

What changed:

- Enabled virtual thread request handling:
  - `spring.threads.virtual.enabled: true`
- Enabled graceful shutdown:
  - `server.shutdown: graceful`
- Added Hikari connection pool defaults for local/default profile.
- Added Actuator endpoint exposure for:
  - `health`
  - `info`
  - `metrics`
- Enabled health probes.
- Added a `prod` profile using PostgreSQL via environment variables:
  - `DATABASE_URL`
  - `DATABASE_USERNAME`
  - `DATABASE_PASSWORD`
  - `DATABASE_POOL_MAX_SIZE`
  - `DATABASE_POOL_MIN_IDLE`
  - `DATABASE_CONNECTION_TIMEOUT_MS`
- Set production JPA DDL mode to `validate`.

Why:

The default app used H2 and `ddl-auto: update`, which is convenient locally but not production-grade for multi-user persistent data. The new `prod` profile makes the intended production database setup explicit while leaving local H2 behavior intact.

Actuator health and metrics endpoints give deployment platforms and operators basic visibility into whether the service is alive and how it is behaving. Graceful shutdown helps avoid cutting off in-flight screening requests during deploys or restarts.

### Added production dependencies

Files changed:

- `pom.xml`

What changed:

- Added `spring-boot-starter-actuator`.
- Added PostgreSQL runtime driver dependency.

Why:

Actuator supports production health/metrics endpoints. The PostgreSQL driver supports the new `prod` profile without changing local H2 development behavior.

### Updated unit test wiring

Files changed:

- `src/test/java/com/recruiter/web/HomeControllerTest.java`

What changed:

- Updated direct `HomeController` construction to pass the newly required collaborators.
- Added a small direct executor test helper so controller tests can run deterministic background work.
- Updated the existing `home(...)` test call to match the current controller method signature.

Why:

The production controller now depends on the managed executor and review store. The tests needed equivalent explicit wiring for direct construction.

## Behavior Preservation

The refactor did not intentionally change:

- Candidate scoring calculations
- AI prompt contents
- Screening package behavior
- Ranking and shortlisting rules
- Templates or user-facing page flow
- Persistence entity mapping
- File validation limits
- Existing local H2 startup behavior

The concurrency changes affect how much work can run at the same time, not how a single screening run is scored or displayed.

## Verification

Targeted tests for the changed areas pass:

```bash
./mvnw -Dtest=HomeControllerTest,CandidateScreeningFacadeTest test
```

Result:

- 24 tests run
- 0 failures
- 0 errors

Full test suite status:

```bash
./mvnw test
```

The full suite still has 2 failures in `CandidateScoringServiceTest`:

- `defaultHeuristicWeightsProduceCurrentScore`
- `customHeuristicWeightsChangeScoreProportionally`

Both failures expect older numeric scores (`61.7` and `62.3`) but the current implementation returns `71.0`. The refactor did not modify `CandidateScoringService` or scoring logic, so these failures appear isolated to existing scoring expectations rather than the production-readiness changes.

## Operational Notes

For production, run with the `prod` profile and provide the required database environment variables:

```bash
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=jdbc:postgresql://host:5432/recruiter
DATABASE_USERNAME=...
DATABASE_PASSWORD=...
```

The concurrency limits should be tuned based on real infrastructure capacity, AI provider rate limits, and database pool size. The defaults are conservative starting points, not final capacity-planning numbers.
