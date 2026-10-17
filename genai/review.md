# Review of the AI-generated task API

Subject: `genai/output/` (a single-pass answer to `genai/prompt.md`, kept unedited). Result of the review: `genai/corrected/`.

The output is better than a typical first draft. The core security property (a user cannot read or change another user's task) is right on the first try, and most of the "classic" flaws one would expect are absent. The flaws that are present are smaller but real, and two of them are only visible when you actually run the code.

## 1. How it was validated

Everything below was done on this machine (JDK 21, Maven wrapper from `backend/`, Docker Desktop for Testcontainers).

| Step | What was done | Result |
|---|---|---|
| Compile + test, as delivered | Copied `output/` (pom + `src`) to a scratch folder outside the repo and ran `mvnw test` | **Main code compiles (16 files). Test compilation fails, 0 tests run.** |
| Compile + test, one-word fix | Same copy, only `TestSecurityConfig` made `public` | 43 tests, 0 failures (`TaskServiceTest` 13, `TaskControllerTest` 30) |
| Read every line | All 16 main files, the migration, `application.yml`, `pom.xml`, both test classes and `TestSecurityConfig`, checked against each numbered constraint of the prompt | Findings in section 3 |
| Probe the running web layer | A throw-away `@WebMvcTest` (not kept) that sent ~17 awkward requests: wrong Accept header, enum as number, title as number, `due_date` with time/offset, camelCase `dueDate`, padded title, empty/`null`/array body, duplicate parameters, ID overflow, unknown path, missing content type | Showed items 2, 3, 4, 8 below |
| Run against PostgreSQL | Added Testcontainers ITs to `corrected/` (they run the real Flyway migration with `ddl-auto=validate` and exercise the queries). They were run against the **unchanged AI main code** first | 11 ITs pass: migration and mapping are consistent, ownership and sorting work in the database |
| TDD for the fixes | New failing tests first, then fixes (commits `[red]` then `[green]`) | 16 red failures: 15 turned green, 1 test dropped (item 8); see section 4 |

Compile error as delivered (verbatim, shortened):

```text
TaskControllerTest.java:[35,25] com.example.tasks.TestSecurityConfig is not public in com.example.tasks; cannot be accessed from outside package
TaskControllerTest.java:[45,9]  com.example.tasks.TestSecurityConfig is not public in com.example.tasks; cannot be accessed from outside package
BUILD FAILURE ... testCompile ... 2 errors
```

The prompt asked for "complete, compilable files only". The model never compiled what it wrote, and the claim of 43 passing tests would have been unverifiable without running it.

## 2. What was not checked

- The platform's JWT filter chain and `users` table do not exist in this exercise. The ITs use a test stub table `users(username)`; the real name and key are an assumption the AI also made.
- No load or concurrency tests. Lost updates (item 7) are reasoned about, not reproduced.
- Behaviour behind a reverse proxy (`Location` header host, forwarded headers) was not tested.

## 3. Issues found

Line numbers refer to files in `genai/output/`. "Test" names refer to `genai/corrected/src/test/java/com/example/tasks/`.

| # | Issue | Where | Why it matters | Severity | Correction | Test that covers it |
|---|---|---|---|---|---|---|
| 1 | The delivered tests do not compile: `TestSecurityConfig` is package-private but imported from `web.TaskControllerTest` | `src/test/.../TestSecurityConfig.java:12`, used at `TaskControllerTest.java:35,45` | `mvn test` fails before running anything; the "all tests pass" impression is false. The pom was never built by the author | High | Class made `public` (the only change in the baseline commit of `corrected/`) | Whole suite now builds |
| 2 | A client `Accept` header the API cannot satisfy (`application/xml`) produces **500**, and an `ERROR` log with stack trace | `GlobalExceptionHandler.java:179-184` (catch-all `Exception` handler; no handler for `HttpMediaTypeNotAcceptableException`) | A client mistake is reported as a server fault, pollutes error monitoring and alerts. The handler list looks exhaustive (405, 415, 404, 409) but misses the fourth negotiation error | Medium | Added `HttpMediaTypeNotAcceptableException` handler returning 406 problem+json | `TaskControllerHardeningTest.acceptHeaderWeCannotSatisfyIsA406NotA500` |
| 3 | `status` is accepted as a number: `{"status": 1}` becomes `IN_PROGRESS`; `2` becomes `DONE`, on POST and PUT | `application.yml` (no Jackson setting), `CreateTaskRequest.java:15`, `UpdateTaskRequest.java:15` | Prompt: "status enum TODO/IN_PROGRESS/DONE". Jackson's default accepts the enum ordinal, so the API contract silently depends on declaration order; inserting a value later changes what existing clients mean | Low to medium | `spring.jackson.deserialization.fail-on-numbers-for-enums: true`; the existing handler turns it into a 400 with `errors[].field = status` | `statusSentAsEnumOrdinalIsRejected`, `...OnPutToo` |
| 4 | `due_date` with a time part is accepted and the time and zone are **discarded**: `2999-01-01T23:30:00Z` and `2999-01-01T23:30:00` become `2999-01-01` | `CreateTaskRequest.java:16`, `UpdateTaskRequest.java:16` (Jackson's default `LocalDate` deserializer is lenient) | This is the timezone handling flaw. A client that sends an instant gets a different calendar day than it meant, with no error. The prompt specified an ISO date | Medium | `StrictIsoDateDeserializer`: only `yyyy-MM-dd`; anything else is a 400 on `due_date` | `dueDateWithATimeComponentIsRejectedInsteadOfSilentlyTruncated` (3 inputs), `...OnPutToo`, `impossibleCalendarDateIsRejected` |
| 5 | The "not in the past" rule exists twice, with two different clocks. `@FutureOrPresent` on the DTO uses the JVM clock and zone and fires first; the service check with the injected `Clock` is unreachable over HTTP. `ClockConfig` uses `Clock.systemDefaultZone()`, so "today" depends on the host | `CreateTaskRequest.java:16`, `TaskService.java:34-37`, `ClockConfig.java:13` | (a) Behaviour changes with the server zone: a 23:30 deployment in UTC-10 and one in UTC differ on what is "today". (b) The boundary cannot be tested through the controller. (c) Two messages for one rule (`must be a date in the present or in the future` vs `must not be in the past`). (d) The AI's own test `TaskControllerTest.java:144-152` passes only because of the dead-code ordering, and the service test never meets the controller | Medium | Removed `@FutureOrPresent`; the service is the single authority. `Clock` uses `app.time-zone` (default `UTC`, env `APP_TIME_ZONE`). The old controller test now checks the exception mapping | `DueDateClockTest` (3 tests, fixed clock 2020-01-01), `ClockConfigTest` (2 tests, host zone forced to Tokyo) |
| 6 | The page-size cap exists only as `@Max(100)` on the controller parameter. The service accepts any size and throws `IllegalArgumentException` (a 500 via the catch-all) for size 0 or a negative page | `TaskController.java:52-53`, `TaskService.java:49-50` | "Sensible max page size" is a domain rule; a second caller (another controller, a job) bypasses it and can request an unbounded page | Low | Service validates and throws `TaskValidationException` (400 field errors); controller annotations kept for early feedback | `TaskServicePagingTest` (3 tests) |
| 7 | PUT is last-write-wins: no `@Version`, no `If-Match` | `Task.java` (no version field), `TaskService.java:58-64` | Two clients editing the same task silently overwrite each other | Low | **Not fixed.** A correct fix changes the contract (version in the body or `If-Match`/ETag, plus a 409/412), which the prompt did not ask for. Left as an open decision | none |
| 8 | A JSON number is accepted where text is expected: `{"title": 123}` is stored as `"123"` | `CreateTaskRequest.java:13` | Weak input typing; low impact | Low | **Not fixed.** `allow-coercion-of-scalars=false` has no effect on `String` targets (verified: still accepted); the real fix is a Jackson `CoercionConfig`, which needs extra configuration the web slice does not load. Not worth the complexity here | none (the test I wrote was removed once the approach failed) |
| 9 | The AI's tests mock the repository, so the migration, the entity mapping (`ddl-auto=validate`), the ownership query and the `NULLS LAST` ordering were never executed. "Ownership" is verified only as "the service passes the right arguments to a mock" | `TaskServiceTest.java` (all `@Mock TaskRepository`), no `@DataJpaTest`/IT in `output/` | The core security property rests on one derived query and one SQL migration that no test touches. This is the classic "mocks hide the bug" gap | Medium | Added `TaskRepositoryIT` (migration + validate, owner scoping, ordering, paging, DB constraints) and `TaskApiIT` (whole stack, two users: GET/PUT/DELETE of another user's task give 404 and leave the data untouched, body `owner` ignored, list scoped). Both pass on the original code, so they are evidence, not a fix | `TaskRepositoryIT` (5), `TaskApiIT` (6) |
| 10 | 401 is only tested for the status code, against a test entry point that returns an empty `HttpStatusEntryPoint` | `TestSecurityConfig.java:20`, `TaskControllerTest.java:384-406` | The prompt wants problem+json for every error; the 401 body and `WWW-Authenticate` are not verified anywhere. The AI said so in its assumptions (platform concern) | Low | **Not fixed** (outside this module's boundary, as stated in the assumptions) | none |
| 11 | A valid token for a username missing in `users` ends in `DataIntegrityViolationException` on the FK, mapped to **409 "conflict"** | `V1__create_tasks_table.sql:105`, `GlobalExceptionHandler.java:171-177` | The real situation is an authentication-state problem (deleted user, token still valid), not a resource conflict; the client cannot tell. Also every other integrity error (including real bugs) becomes 409 | Low | **Not fixed**; needs a decision on how the platform reports a deleted user. The IT documents that the database rejects the orphan | `TaskRepositoryIT.databaseRejectsATaskForAnUnknownOwner` |
| 12 | Unknown JSON properties are ignored, so a typo such as `dueDate` instead of `due_date` silently drops the date (201 with `due_date: null`) | `application.yml` (Jackson default) | Silent data loss for the client. The AI listed it as an assumption, and it is also what protects against mass assignment (`owner` is ignored, which is good) | Low | **Left as is** by choice; failing on unknown properties would also be defensible. `TaskApiIT.ownerInTheBodyIsIgnored` pins the safe half | `TaskApiIT.ownerInTheBodyIsIgnored` |
| 13 | `created_at` / `updated_at` use `Instant.now()` inside JPA callbacks, not the injected `Clock`; the DB default `now()` is unused | `Task.java:59-69` | Timestamps cannot be controlled in tests; two sources of time in one app | Low | **Not fixed** (cosmetic for this exercise, no requirement depends on it) | none |

Counted as corrected: items 1 to 6 and the test gap in 9. Items 7, 8, 10 to 13 are documented and left open on purpose.

## 4. Test evidence (TDD)

| State | Command | Result |
|---|---|---|
| `output/` as delivered | `mvnw test` | **Build failure**, test compilation (0 tests run) |
| `output/` plus `public` on one class | `mvnw test` | 43 tests pass |
| `corrected/` at the `[red]` commit (original main code, new tests; re-run from a `git archive` of that commit) | `mvnw test` and `verify` | 62 unit/web tests, **16 failures** (all in new tests exposing items 2 to 6 and 8); the AI's 43 still pass; 11 ITs pass |
| `corrected/` at the `[green]` commit | `mvnw verify` | 61 unit/web tests + 11 ITs, **0 failures** |

Mistakes of mine found on the way, not AI flaws: the first version of the "host zone" test compared `ZoneId.of("UTC")` to `ZoneOffset.UTC` (never equal), so it would have failed even on correct code; it was fixed in the green commit and still fails against the original (Tokyo is not UTC). The Accept test first got a 200 because the mocked service returned `null`; it now stubs a real page so the 500 on serialization is reached. The `title: 123` test could not be made green with configuration alone (item 8) and was removed in the green commit rather than left red.

## 5. Edge cases

Verified (by probe and now by tests where noted):

- Past, today and future `due_date` on create, against a fixed clock (`DueDateClockTest`); PUT accepts any date, as the prompt only restricts create. Leaving an already-late task editable is the intended reading.
- Time part, offset, impossible dates (`2999-02-30`) and non-ISO text in `due_date`: 400 with `errors[].field = due_date`.
- Unknown enum name, enum ordinal, lowercase name: 400 with the allowed values in the message (ordinals fixed).
- Empty body, `null`, array body, malformed JSON, wrong content type (415), unsupported method (405), unknown path (404), non-numeric or overflowing id (400): all problem+json with `instance`.
- Page below 0, size 0, size 101, non-numeric page: 400, in the controller and now also in the service.
- Same sort for every page: `due_date ASC NULLS LAST, id ASC` (checked on PostgreSQL in `TaskRepositoryIT`; the `id` tie-break gives stable paging).
- Second delete of the same id and deleting another user's id both return 404.
- Whitespace-padded titles (`"  a  "`) are stored untrimmed; a title of only spaces is rejected by Bean Validation and by the DB check. Left as is.

## 6. Authentication and ownership

- The username comes from `Authentication.getName()` in the controller only (`TaskController.java`); there is no request parameter or body field for it, and a body `owner` is ignored (`TaskApiIT`).
- Every read, update and delete goes through `findByIdAndOwnerUsername`, one atomic query, so there is no check-then-act gap and no IDOR. A task of another user and a missing task give the same 404 body, so existence is not revealed. The AI chose 404 over 403 explicitly and says so.
- `owner_username` is `updatable = false` on the entity, so even a coding mistake in the service could not move a task to another user.
- The list queries filter by owner in JPQL and the count query too, so `totalElements` does not leak other users' counts.
- Weak points that remain: the controller trusts `Authentication` is present and non-anonymous (a misconfigured platform that lets anonymous through would produce `anonymousUser` tasks, stopped only by the FK); and the 401 body format is the platform's (item 10).

## 7. Validation

- Request validation is done with Bean Validation on records (title not blank and at most 200, description at most 2000, status not null on PUT) and a handler that turns each violation into `errors[{field, message}]` with snake_case names. The JSON field name `due_date` is reported correctly even for deserialization errors.
- Database constraints mirror the rules (`VARCHAR(200)`, `VARCHAR(2000)`, status `CHECK`, blank-title `CHECK`), so a bypassed validation layer still cannot store bad data. `TaskRepositoryIT` proves it for the blank title.
- Gaps were the type-level leniency of Jackson (items 3, 4, 8) and the duplicated date rule (item 5).

## 8. What the AI got right

Not flaws, listed so the review is balanced (all checked by reading and, where possible, by running):

- Ownership and IDOR: correct, see section 6. No 403/404 existence leak.
- Layering and DTOs: controller depends on the service only; `Task` never appears in a response or request; mapping is explicit. Response has no `owner`.
- The prompt's requirements for status codes and headers: 201 with `Location`, 204 on delete, 404, 400 with `errors`. Cap enforced as a 400, not a silent clamp, and stable sort with an id tie-break.
- Error handling: one `@RestControllerAdvice`, problem+json everywhere, no stack traces or exception text in 500s (`server.error.*` also set to `never`), the enum failure message lists the legal values.
- Persistence: `ddl-auto=validate` plus Flyway, correct types (`DATE` for a calendar date, `timestamptz` for audit), owner/due-date and owner/status indexes, `open-in-view=false`. The migration and mapping validated on a real PostgreSQL 16 on the first run.
- A `Clock` was injected into the service for testability, and the unit tests are meaningful (they assert that nothing is saved or deleted for another user's task).
- The assumptions list at the end of the answer is honest: it named the 404 choice, the PUT semantics, the placeholder `type` URIs and the platform dependency for 401.

## 9. Lessons

1. Run what the model gives you. The one failure a build catches in two seconds (item 1) is invisible to a reader.
2. Look for rules implemented twice (item 5): the dead copy passes its own tests and hides which one governs.
3. Framework defaults (Jackson leniency, the catch-all handler) are where the surprises are; the model handled the explicitly asked-for rules well and the implicit contract less well.
4. Passing tests that mock the persistence layer say nothing about the persistence layer; add one real-database test for the security property.
