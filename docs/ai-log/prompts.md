# AI Prompt Log

Chronological log of the prompts used to build this project with Claude (agentic coding). It backs the "GenAI tools" part of the presentation: what was asked, what came back, and what was accepted, changed or rejected.

---

## 001 — Planning, repository structure, first TDD cycle (Sat 17 Oct 2026)

**Context given before the prompt:** the exercise PDF, and a short discussion that settled the stack (Testcontainers instead of H2, Caffeine for caching, TanStack Query for server state).

**Prompt (translated from Russian):**

> Create the plan and the repository structure for:
>
> - Backend: Java 21, Spring Boot 3, PostgreSQL. Testcontainers for tests, plus JWT, Caffeine cache and Flyway.
> - Frontend: React, TypeScript and Vite, data via TanStack Query.
> - Run: `docker-compose` starts the DB, API and frontend with one command. Seed data: a demo user and a batch of Pokémon.
> - GenAI part: a separate `genai/` folder with the prompt, the code and the analysis.
>
> After the plan, run the first TDD cycle on US01.
>
> Also add a breakdown into simple tasks for subagents; I will launch them after the skeleton.

**Output:** `docs/PLAN.md`, `docs/TASKS.md`, backend skeleton, US01 tests and implementation.

**Review notes:**

- Checked that `domain` and `application` have no Spring imports; added an ArchUnit test so this is enforced, not just promised.
- PokeAPI "category" is not on the Pokémon resource: it is the English `genus` of the species. The species is resolved through the `species` link, because form names such as `deoxys-normal` are not valid species names.
- `@Cacheable` does not work on self-invocation, so the HTTP client (cached) and the mapping adapter (parallel fan-out) are separate beans.
- Page size is capped at 50 to protect the upstream API; covered by unit and web tests.

---

## 002 — T1 US02 detailed view (subagent)

**Prompt (summarised):** a subagent received the T1 task from `docs/TASKS.md` plus the shared rules: implement `GET /api/v1/pokemon/{idOrName}` (image, stats, English description, evolution chain) with strict TDD (separate `[red]` and `[green]` commits), matching the US01 style and the Clean Architecture rules, with small hand-trimmed PokeAPI fixtures.

**Output:**

- Domain: `PokemonDetails` with nested `Stat` and `EvolutionNode` records; use case `GetPokemonDetailsUseCase`; service `GetPokemonDetailsService` (identifier must match `[a-z0-9-]{1,100}`, otherwise `InvalidRequestException` on field `idOrName`).
- Outbound: `PokemonCatalog.findDetails`, implemented in `PokeApiCatalogAdapter` (Pokémon -> species -> evolution chain); new cached `PokeApiClient.fetchEvolutionChain(int)` with cache `pokeapi-evolution`; DTOs extended or added for stats, types, artwork, flavour text and the chain.
- Web: `PokemonDetailsResponse` and `PokemonController.details`, wired in `UseCaseConfig`.
- Tests: service (valid, malformed, not found, upstream), adapter (pikachu, branching eevee, ditto without evolutions, missing species, 404, 5xx), cache test for the chain, controller tests for 200/400/404/502. Fixtures for pikachu, eevee and ditto.

**Review notes:**

- Evolution chain is a tree (`evolvesTo`) rather than a flat list, so Eevee keeps all its branches. When the species or chain cannot be loaded, the Pokémon is returned as a one-node chain instead of failing the whole page.
- Only the first `evolution_details` entry is mapped (`minLevel`, trigger). Some species have several alternative ways to evolve; that detail is intentionally dropped.
- Description normalisation collapses every whitespace run, not only `\n` and `\f`; PokeAPI also uses soft line breaks in other forms.
- The first attempt to apply the test edits through a shell heredoc failed because of quoting; the edits were redone from script files. No generated suggestion was rejected on design grounds.

---

## 003 — T2 persistence + US03 sync (subagent)

**Prompt (summarised):** a subagent received the T2 task from `docs/TASKS.md` plus the shared rules and orchestrator notes: copy a Pokémon from PokeAPI into `local_pokemon` idempotently (`POST /api/v1/local-pokemon/sync`: 201 + `Location` for a new record, 200 for a re-sync that refreshes the PokeAPI fields and keeps `localName`, `region`, `notes`, `tags`), plus `GET /api/v1/local-pokemon` and `/{id}`. Strict TDD with separate `[red]` and `[green]` commits, JPA `@Version` for T3, a deterministic `Clock`, Testcontainers for the adapter, and no schema change (`V1__init.sql` is fixed). It had to reuse T1's `idOrName` rule instead of copying the regex.

**Output:**

- Domain: `LocalPokemon` (record with `firstSync` / `resyncedWith`), `PokemonSnapshot` (the PokeAPI-owned fields a sync overwrites), and two shared rules extracted from US01/US02: `PokemonIdentifier` and `PageQuery`.
- Application: `SyncPokemonUseCase` (returns `SyncResult(pokemon, created)`), `ListLocalPokemonUseCase`, `GetLocalPokemonUseCase`, their services and the `LocalPokemonRepository` port; `PokemonCatalog.findByIdOrName`, implemented in `PokeApiCatalogAdapter` on top of the cached `fetchPokemon` / `fetchSpecies`.
- Persistence: `LocalPokemonEntity`, `SpringDataLocalPokemonRepository`, `JpaLocalPokemonRepositoryAdapter` (mapping in both directions, `@Version`, ordered abilities with `@OrderColumn`, tags as a set, lazy collections mapped inside the transaction).
- Web and config: `LocalPokemonController`, `SyncPokemonRequest`, `LocalPokemonResponse` (kg / m like the US01 DTOs, proprietary fields, `version`), `ClockConfig`, three beans in `UseCaseConfig`.
- Tests: domain, three services, catalogue snapshot (bulbasaur, pikachu, ditto, missing species, 404, 5xx), `@WebMvcTest` for 201/200/400/404/502 and the read endpoints, and `JpaLocalPokemonRepositoryAdapterIT` on PostgreSQL (insert, update, ordering, version increments, stale version, pagination, nulls).

**Review notes:**

- The domain `version` is a nullable `Long`, with `null` meaning "not stored yet". A primitive `long` would make Spring Data treat every record with an assigned id as existing and `merge` a row that is not there; the wrapper keeps persist vs merge unambiguous and mirrors JPA.
- The new services would have needed their own copy of the idOrName regex and the page/size limits. Instead those rules were moved into `PokemonIdentifier` and `PageQuery` in a separate `refactor` commit before the red step, and US01/US02 now use them too; their old tests still pass unchanged.
- Re-sync always bumps `version` because `syncedAt` / `updatedAt` change, which is intended: a client holding an older copy must re-read before editing.
- The sync is a read-then-write without a lock, so two concurrent first syncs of the same Pokémon can hit the primary key and return a 500. Left open on purpose; the effect is a retry.

---

## 004 — T4 authentication (subagent)

**Prompt (summarised):** a subagent received the T4 task from `docs/TASKS.md` plus the shared rules and orchestrator notes: register / login / me with HS256 JWT and BCrypt, public versus protected routes (`/api/v1/pokemon/**` public, `/api/v1/local-pokemon/**` protected), 401 and 403 as `application/problem+json`, CORS for the two frontend origins, stateless sessions with CSRF off, a seeded `demo` user, and a startup failure for a short secret. Strict TDD with separate `[red]` and `[green]` commits; the credential rules had to live in the framework-free core.

**Output:**

- Domain and application: `User` (record plus the username and password rules, and the "username taken" conflict), `ConflictException` (409, reusable by T3), `AuthenticationFailedException` (401, one fixed message), `RegisterUserUseCase` / `AuthenticateUserUseCase`, their services, and the `UserRepository`, `PasswordHasher` and `TokenIssuer` ports.
- Adapters: `BCryptPasswordHasher`, `JwtTokenIssuer`, `JpaUserRepositoryAdapter` (+ entity and Spring Data interface), `AuthController` with `CredentialsRequest` / `LoginResponse` / `UserResponse`, and the `ProblemDetail` entry point and access-denied handler.
- Config: `SecurityConfig` (filter chain, `NimbusJwtDecoder` HS256, CORS), `AuthAdaptersConfig` (hasher, encoder, issuer), `JwtProperties` (fails startup under 32 bytes), two beans in `UseCaseConfig`, `app.jwt` / `app.cors` in `application.yml`, and `V2__seed_demo_user.sql`.
- Tests: `UserTest`, `RegisterUserServiceTest`, `AuthenticateUserServiceTest`, `BCryptPasswordHasherTest`, `JwtTokenIssuerTest`, `JwtPropertiesTest`, `ProblemDetailSecurityHandlersTest`, `AuthControllerTest`, `SecurityRulesTest` (public vs protected, valid / expired / foreign-secret / garbage tokens, CORS, no session), `JpaUserRepositoryAdapterIT`, `AuthFlowIT`; the two existing web slice tests import `SecurityConfig` (`LocalPokemonControllerTest` runs as `@WithMockUser`).

**Review notes:**

- Usernames are trimmed and lower-cased before validation, on register and on login, so `Demo` and `demo` are the same account. Login does not re-validate the format: a name that could never be registered is just an unknown user (401), not a 400.
- The suggested "same message for unknown user and wrong password" was not enough on its own: an unknown user skips the BCrypt comparison and answers measurably faster. The login service now compares against a throw-away hash for unknown users, so both failures cost one comparison (unit-tested).
- Password upper bound of 72 bytes was added although the task only says "at least 8": BCrypt silently ignores bytes beyond 72, so a longer password would be accepted but not fully checked.
- `/me` is answered from the token (subject and `ROLE_` authorities) rather than a use case and a database read; the cost is that a deleted user keeps a valid `/me` until the token expires (1 h).
- `AuthenticationFailedException` also sends `WWW-Authenticate: Bearer`, so login failures and filter-chain 401s look identical to a client.
- The seed hash was generated with the real encoder and is asserted in both `BCryptPasswordHasherTest` (literal hash) and `AuthFlowIT` (login as `demo`), rather than trusting a hash copied from the model's memory.
- First attempt at the tests used `"x".repeat(50)` inside `@ValueSource`, which is not a constant expression and did not compile; those cases became separate tests. A strict-stubs failure (`PotentialStubbingProblem`) in the "same message" test was fixed by stubbing the throw-away-hash comparison too, not by loosening the mock. Shell heredocs and `git commit -F -` again mangled multi-line input in PowerShell; files were written with the editor tool and the commit message passed from a file.

---

## 005 — T9 GenAI section (subagents)

**Prompt (summarised):** T9 from `docs/TASKS.md` was run in three phases by separate agents so that the AI output is genuine and the review independent.

1. A subagent wrote only `genai/prompt.md`: a realistic prompt for a task-management REST API (Java 21, Spring Boot 3.5, JPA, PostgreSQL, JWT already provided, Bean Validation) with layering, validation rules, ownership, pagination with a maximum, problem+json errors, status codes, required tests and an output format of complete files plus assumptions. It was explicitly not to be weakened to provoke mistakes.
2. The orchestrator gave that text, and nothing else, to a fresh Claude Sonnet 5.5 subagent in a single pass; its answer was split into `genai/output/` unedited.
3. The same reviewer subagent was then asked to validate it for real (compile, run, read every line against the prompt, probe), write `review.md` and `README.md`, and build `genai/corrected/` test-first with separate `[red]` and `[green]` commits.

**Output:**

- `genai/prompt.md` (39 lines), `genai/output/` (16 main files, migration, `application.yml`, `pom.xml`, 2 test classes, `RESPONSE.md`).
- `genai/review.md`: method, compile/test results, 13-row issue table, edge cases, authentication and ownership, validation, what the AI got right, lessons.
- `genai/corrected/`: the output plus fixes (`StrictIsoDateDeserializer`, `ClockConfig` with `app.time-zone`, service-side paging limits, a 406 handler, Jackson `fail-on-numbers-for-enums`, single due-date rule in the service) and new tests: `TaskControllerHardeningTest`, `DueDateClockTest`, `ClockConfigTest`, `TaskServicePagingTest`, and two Testcontainers ITs (`TaskRepositoryIT`, `TaskApiIT`).
- Results: the raw output does not compile its tests; with a one-word fix its 43 tests pass. Red commit: 62 unit/web tests, 16 failing; green commit: 61 unit/web tests and 11 ITs, all passing.

**Review notes:**

- Rejected as claimed by the AI output: the implied "complete, compilable files" (the test code does not compile: package-private `TestSecurityConfig` used across packages) and "43 passing tests", which nobody could have known without building it.
- Rejected design in the AI output: the due-date rule implemented twice (Bean Validation `@FutureOrPresent` on the DTO with the JVM clock, plus a service check with an injected `Clock` that is unreachable over HTTP) and a `Clock` bound to the host time zone. Replaced by one rule in the service and a configured zone (default UTC). The AI's controller test that passed only through the dead-code ordering was rewritten to check the exception mapping.
- Found only by running: an `Accept: application/xml` request returns 500 because the catch-all handler swallows `HttpMediaTypeNotAcceptableException`; `status` is accepted as an enum ordinal; `due_date` of `2999-01-01T23:30:00Z` is silently truncated to a date.
- Not flaws, to avoid inventing any: ownership (atomic `findByIdAndOwnerUsername`, 404 for foreign and missing tasks, owner only from `Authentication`), DTO/entity separation, problem+json shape, no stack-trace leaks, stable sort and the migration validated against PostgreSQL were all correct on the first pass.
- Mine, corrected on the way: a UTC assertion that compared `ZoneId.of("UTC")` with `ZoneOffset.UTC`; a first Accept test that got 200 because the mock returned `null`; a heredoc with quotes that wrote no files (re-done with the editor tool).
- Tried and abandoned: `allow-coercion-of-scalars=false` does not stop `{"title": 123}` from being coerced to a string (Jackson needs a `CoercionConfig`, which the web slice does not load). The test was removed from the green commit and the limitation is recorded as open in `review.md`.
- Left open on purpose and listed in `review.md`: lost updates on PUT (needs an `If-Match`/version contract), the 401 body format (platform), 409 for a valid token whose user is missing from `users`, silent unknown properties, audit timestamps not from the `Clock`.

---

## 006 — T6 frontend scaffold (subagent)

**Prompt (summarised):** T6 from `docs/TASKS.md`: a `frontend/` with Vite + React + TypeScript, TanStack Query, React Router, ESLint, Prettier, Vitest + Testing Library. An API client with a base URL from `VITE_API_URL`, JWT handling and problem+json parsing into a typed `ApiError`; DTO types from the backend; an auth context with `ProtectedRoute`; `usePokemonPage` with `keepPreviousData`; a layout and the Browse page with `?page=` in the URL. The orchestrator added constraints that come from backend behaviour: the token must only go on requests that need it (an expired token gets 401 even on public routes), a 401 must sign the user out, an empty `VITE_API_URL` means same-origin, non-JSON error bodies (nginx 502) must not break error handling, and the browser console must stay clean. Commit order was scaffold, then `[red]`, then `[green]`.

**Output:**

- Commit 1: generated Vite template with the demo content removed, tooling config and one smoke test.
- Commit 2 (`[red]`): 11 test files, 110 tests, all failing for missing modules. Covers the client (URL resolution, token placement, error parsing, 401), the session store, `AuthProvider`, `ProtectedRoute`, the query client, `usePokemonPage`, `format`/`pagination` helpers, `Pagination`, `BrowsePage` and the route table. The test setup fails any test that writes to `console.error`/`console.warn`, which turns "zero console warnings" (React keys, `act()`, router) into an enforced rule.
- Commit 3 (`[green]`): the implementation, a single stylesheet, placeholder routes for the pages T7 will replace. `npm run build`, `lint` and `test` pass (110 tests). Smoke-tested in the browser pane against a backend built from the current `main`: Browse renders, `?page=2` and the Next link work, no console output under `StrictMode`, no horizontal scroll at 375 px.

**Review notes:**

- Corrected by running: `jsdom` 30 (the newest) throws `ERR_REQUIRE_ESM` on Node 20.15, so `jsdom@26` is pinned; Vite 6 and Vitest 3 were chosen for the same Node reason.
- Corrected by running: a context object in `auth/authContext.ts` next to the provider `auth/AuthContext.tsx` resolved to the wrong file on Windows (case-insensitive), giving "Element type is invalid". Renamed to `context.ts`. A bash heredoc also silently truncated several files containing quotes, so the files were written with the editor tool and the result checked.
- Design choices I made rather than accepted from a default: the token is read by the client from a small session store (not passed through React), so a 401 can clear it from plain code and the UI follows through `useSyncExternalStore`; the stored value is validated and dropped when malformed or expired; `VITE_API_URL` is resolved at call time so `undefined` and `''` are tested separately.
- Found by the browser check, not by tests: an older backend container on :8080 (built before the CORS config) answered without `Access-Control-Allow-Origin`, so the first smoke test failed with a CORS error. It was not touched; a throw-away backend built from a copy of the current source on :8081 and its own Postgres container on :5433 were used instead, then removed.

---

## 007 — T3 US04 local modification (subagent)

**Prompt (summarised):** T3 from `docs/TASKS.md`: `PUT /api/v1/local-pokemon/{id}` (`localName, region, notes, tags[], version`) and `DELETE`, with 400/404/409/401, bean validation for shape and business rules in a framework-free service, and one `*IT` for the stale-version 409. The orchestrator added: reuse T4's `ConflictException`; check the version in the service and also translate `OptimisticLockingFailureException` in the JPA adapter so a 500 is impossible; fix T2's concurrent-first-sync race (`DataIntegrityViolationException` on insert); unknown or read-only body fields are 400 per DTO, not by flipping the global Jackson setting; the 400 must name the offending property.

**Output:**

- Commit `[red]` (`062730d`): tests plus compile stubs. 34 `UpdateLocalPokemonServiceTest` cases, 5 `DeleteLocalPokemonServiceTest`, 31 `LocalPokemonModificationControllerTest` (200, every 400 shape, 404, 409, 401 with `@WithAnonymousUser`, 204), 2 domain, 2 in `SecurityRulesTest`; the adapter IT gains five tests (stale version to `ConflictException`, two updates from one version, update after delete, duplicate insert, delete with collections) and `UpdateLocalPokemonIT` (7 tests over real HTTP with the seeded `demo` user: update then replay with the old version gives 409 problem+json).
- Commit `[green]` (`36f8b95`): `UpdateLocalPokemonService`, `DeleteLocalPokemonService`, `UpdateLocalPokemonRequest`, controller handlers, `handleHttpMessageNotReadable` in `GlobalExceptionHandler` (add-only), adapter translation, `deleteById` on the port. `.\mvnw.cmd verify`: 306 unit/web tests and 35 ITs, all green.
- Where each rule lives: DTO (shape on the raw values): `version` required and not negative, `localName`/`region` at most 100, `notes` at most 1000, at most 10 tags of at most 50 characters, no unknown or read-only property. Service (business, fails before the store is touched): `id >= 1`, trim, a present-but-blank `localName` is 400, blank `region`/`notes` become `null`, tags trimmed and lowercased then must be kebab (`tags[i]` names the index), duplicates collapse silently, at most 10 distinct remain, 404 for a missing record, 409 when the version differs. Adapter: the race between check and write (409), a lost insert race on the primary key (409, "synced concurrently").

**Review notes:**

- Rejected from the brief: `@JsonIgnoreProperties(ignoreUnknown = false)` on the DTO. Spring Boot turns `FAIL_ON_UNKNOWN_PROPERTIES` off and a `false` annotation only defers to the mapper, so it would not have rejected anything. Replaced by a `@JsonAnySetter` that throws, which keeps the strictness on this DTO only. Two things found by running it: `UnrecognizedPropertyException.from(null, ...)` throws an NPE (no parser in an any-setter), so a small subclass supplies the instance; and Jackson wraps what an any-setter throws, so the handler walks the cause chain.
- Chosen instead of the suggested `deleteById` plus `existsById`: a single bulk `DELETE ... WHERE id = ?` that returns the row count. That is one round trip with no check-then-act race, and an edit that lands between a read and the delete cannot turn the delete into a version failure. The collection tables go through `ON DELETE CASCADE`, which the IT checks.
- Checked rather than assumed: updating a record that another request deleted does not re-insert it (Hibernate's merge of a versioned entity fails with an optimistic-locking error, now a 409); the IT covers it.
- Duplicate tags: de-duplicated silently, not an error, since tags are a set. The DTO's "at most 10" applies to the raw list, the service's to the distinct tags, so the DTO is the stricter of the two; a whitespace-padded value over a limit is rejected by the DTO although the service would have trimmed it under the limit.
- Mine, corrected on the way: a test helper named `put` that shadowed the static import of `put(...)` (compile error); a `when(...)` re-stub that called my own `thenAnswer` with `null` (use `doThrow().when()`); an `assertInvalid` that checked `never().save` after a successful save in the same test. A bash heredoc containing quotes failed to parse twice, so the multi-line edits were done from a script file instead.
- Left open: Jackson still coerces scalars (`"localName": 123` becomes `"123"`, `"version": 1.5` becomes `1`), the same limitation recorded in entry 005; there is no `ETag`/`If-Match` header, the version travels in the body as specified.

---

## 008 — T10 OpenAPI and seed data (subagent)

**Prompt (summarised):** T10 from `docs/TASKS.md`: springdoc OpenAPI with a bearer JWT scheme, `@Operation` and error `@ApiResponse` annotations on the controllers, Swagger UI public; and a demo seeder that pre-syncs Pokemon #1 to #12 into `local_pokemon`, guarded by `app.seed.enabled` (default true, false in tests), idempotent, never calling PokeAPI in tests. The orchestrator added: pick a springdoc 2.8.x that fits Boot 3.5.6; do not create `src/test/resources/application.yml` (it would shadow the main one); startup must not fail or stall when PokeAPI is unreachable; the seeder uses only inbound ports; two red/green cycles (`openapi`, `seed`).

**Output:**

- `openapi` red (`e74282b`): `OpenApiIT` (39 cases over real HTTP, no token): all 8 paths, the `bearerAuth` http/bearer/JWT scheme, `security` present on the six protected operations and absent on the four public ones, 21 documented error responses as `application/problem+json` with a `ProblemDetail` schema, a summary and tag on every operation, success bodies described, Swagger UI served without a token.
- `openapi` green (`bee0cf4`): springdoc 2.8.17 (`springdoc.version` property), `OpenApiConfig` (title, description, version from `build-info.properties` via the `build-info` goal, fallback `dev`), `ApiDocs` constants and `ProblemResponses` composed annotations (`@ProblemResponses.NotFound` etc.), annotations on the three controllers; `@SecurityRequirement` at class level on `LocalPokemonController`, per method on `/auth/me`.
- `seed` red (`6410568`): `DemoDataSeederTest` (13), `SeedConfigTest` (12, `ApplicationContextRunner`, no database), stubs, and `src/test/resources/config/application.yml` with `app.seed.enabled: false`.
- `seed` green (`ba5c092`): `adapter/in/seed/DemoDataSeeder` (an `ApplicationRunner`, only `GetLocalPokemonUseCase` and `SyncPokemonUseCase`), `SeedProperties`, `SeedConfig`, `app.seed.*` in `application.yml`; two `*IT`: `DemoDataSeedIT` (fake PokeAPI on a JDK `HttpServer` serving the fixtures, #2 answers 500: #1 is stored in the background, #2 fails, health stays UP, a second pass after a local edit makes no PokeAPI call and keeps the edit) and `DemoDataSeedPokeApiDownIT` (nothing listens: starts healthy, every id reported failed). `.\mvnw.cmd verify`: 331 unit/web tests and 76 ITs green.

**Review notes:**

- Chosen, not defaulted: the seeder runs on its own daemon thread (`SimpleAsyncTaskExecutor`), because with PokeAPI down 12 ids at a 3 s connect timeout would hold a synchronous runner for over half a minute. The executor is not a bean: a public `Executor` bean makes Spring Boot back off from its own task executor. "Stored" is decided with `GetLocalPokemonUseCase.get` (`ResourceNotFoundException` means missing), so an existing record is never re-synced, not even to refresh it. A Pokemon the user deleted comes back on the next restart; that is the price of a stateless idempotent seed, and `app.seed.enabled=false` turns it off.
- Rejected from the brief: repeated `@ApiResponse(content = ...)` blocks on every method. Replaced by composed annotations, which springdoc reads as meta-annotations (checked by the IT). Corrected by running: springdoc adds the default 200 only when no `@ApiResponse` is declared, so the GET endpoints lost their 200 as soon as an error response was added; the success responses are now explicit and a test checks every one has a body schema.
- Mine, corrected on the way: `String.valueOf(call.getArgument(0))` in a Mockito answer resolves to the `char[]` overload (ClassCastException, caught by the seeder and shown as "failed", which is how the test caught it); re-stubbing a mock with `when(mock.get(1))` calls the earlier stub, so those became `doReturn/doThrow().when()`; bash heredocs with accents and quotes failed to parse twice, so files were written with the editor tool.
- Left open: the demo password appears in the OpenAPI description (it is the documented `demo` user from V2); `springdoc.api-docs.enabled` / `swagger-ui.enabled` are not switched off in any profile because the brief wants Swagger public; the seeder is not retried within a run, only on the next start.

---

## 009 — T7 frontend pages (subagent)

**Prompt (summarised):** T7 from `docs/TASKS.md`: replace the placeholder pages of T6 with the detail page (artwork, types, stat bars, description, evolution tree, "Add to my collection"), login and register forms, the protected collection list (paginated, delete with confirmation) and the edit form (`localName`, `region`, `notes`, tag chips, `version`, 409 handling). The orchestrator added: the backend contract (evolution chain is a tree, PUT body is exactly five properties, 201 vs 200 on sync), client-side rules mirroring the server, TDD with `[red]`/`[green]` commits per cycle, no console warnings (the test setup fails on any `console.error/warn`), and an optional smoke test against a real backend.

**Output:**

- Four red/green cycles. (1) `validation.ts` (username, password in UTF-8 bytes, local name, region, notes, tags, `addTag`, `toUpdateRequest`), `apiRequestWithStatus` so `syncPokemon` can tell 201 from 200, `updateLocalPokemon`, `deleteLocalPokemon`. (2) Detail page: `StatBars` (`role="meter"`, max 255), `EvolutionTree` (nested lists, current species not a link), sync mutation, anonymous visitors sent to `/login` with `state.from`. (3) `AuthForm` shared by login and register, `Field` (label, hint, error wired with `aria-describedby`/`aria-invalid`), `getReturnPath` (never leaves the app), focus moves to the first invalid field. (4) Collection page with `ConfirmDialog` (`alertdialog`, focus on Cancel, Tab trap, Escape, focus returns to the opener), optimistic delete with rollback, edit page with `TagInput`, `409` banner plus Reload that remounts the form from a fresh fetch. One small fix cycle after the smoke test (a rejected tag got a stray trailing comma).
- 259 tests in 18 files (110 before T7); `npm run build`, `npm run lint`, `npm test` green. Smoke test against the real backend (jar from main, temporary Postgres on :5433, Vite on :5173 with `VITE_API_URL=http://localhost:8081`): anonymous "Add" went to login and came back, 401 message, 201 then 200 messages, edit and save, a real 409 produced by a second PUT then Reload, delete through the dialog, no horizontal scroll at 375 px. Everything was stopped and the container removed afterwards.

**Review notes:**

- Decided, not defaulted: login only requires both fields to be non-empty (the format rules would lock out an old account for no gain), register applies the full rules. Save is disabled while the 409 banner is up, otherwise the user could only produce the same 409 again; what they typed stays visible until they press Reload. The form keeps the `version` it was filled from, so a background refetch cannot make a stale form silently overwrite someone else's change. The edit page always refetches on open (`staleTime: 0`); a cached copy would send an old version and cause a needless 409. A delete that answers 404 is treated as success. After a save the user goes to `/collection` with a one-line notice in the router state.
- Rejected from my own first draft: a `<dialog>` element with `showModal()` (jsdom does not implement it, so the tests would need polyfills) and `window.confirm`; a hand-rolled `role="alertdialog"` div is testable and just as accessible. Also rejected: keeping the tag draft inside `TagInput` (Save could not include text still typed there), and `aria-label="Tags"` on the chip list (it made `getByLabelText('Tags')` match both the list and the input, which is also what a screen reader would announce twice), so the list is "Current tags".
- Mine, corrected on the way: the draft-tag code appended an empty leftover, leaving `bad tag,` in the input after a rejected Enter (found in the browser, then covered by a red test); a test that stubbed one pending promise for both register and the follow-up login; and the bash heredocs with accents/quotes failed to parse again, so files were written with the editor tool.
- Left open: no ETag/If-Match (the version travels in the body as specified); the collection list is cached for the default 5 minutes, so a change made from another tab shows up on the next invalidation or reload; the notice after saving lives in history state, so it reappears when that history entry is reloaded.

---

## 010 — T5 Cucumber acceptance tests (subagent)

**Prompt (summarised):** T5 from `docs/TASKS.md`: executable specification, one feature file per user story (`us01_enumeration` to `us04_local_modification`) on Cucumber with Spring, RestAssured for the requests, WireMock as PokeAPI and Testcontainers PostgreSQL, run by `mvn verify` through a runner ending in `IT`. The orchestrator added: reuse the existing JSON fixtures, clear the Caffeine caches and reset WireMock in a `@Before`, clean `local_pokemon` between scenarios, make sure Surefire does not also run the features, Scenario Outlines for the 400 rules, a commit for the infrastructure and a second for the scenarios, and report (not silently weaken) anything that fails.

**Output:**

- Commit `build(acceptance)`: `cucumber-java`/`cucumber-spring`/`cucumber-junit-platform-engine` 7.15.0 (`cucumber.version`), `junit-platform-suite` and `rest-assured` (Boot BOM), `wiremock-standalone` 3.13.1 (`wiremock.version`), all test scope and add-only in `pom.xml`. `CucumberAcceptanceIT` (suite, glue `com.ilia.pokedex.acceptance`), `CucumberSpringConfiguration` (`@SpringBootTest(RANDOM_PORT)`, `@ServiceConnection` PostgreSQL as a `@Bean`, `pokeapi.base-url` from WireMock), `Hooks`, `FakePokeApi`, `ScenarioContext`, `junit-platform.properties`, and one health scenario.
- Commit `test(acceptance)`: `us01_enumeration` (11 scenarios), `us02_detailed_view` (13), `us03_data_sync` (12), `us04_local_modification` (25), `auth` (10), `health` (1) = 72 scenarios, 403 steps, and the step classes `CommonSteps`, `AuthSteps`, `CatalogSteps`, `CollectionSteps`. `mvn test` still runs 331 tests and no scenario; `mvn verify` runs those 331 plus 76 + 72 ITs, all green.

**Review notes:**

- Chosen, not defaulted: the container is a `@Bean @ServiceConnection` inside a `@TestConfiguration` imported only by the Cucumber configuration. The `@Testcontainers`/`@Container` pair the other ITs use is a JUnit extension, which Cucumber does not run; Spring starts and stops a bean container itself. The scenario-scoped `ScenarioContext` is registered the same way, not with `@Component`: a stereotype in the `com.ilia.pokedex` test tree would be component-scanned by every other `@SpringBootTest` and fail there because the `cucumber-glue` scope does not exist in those contexts.
- Rejected from my own first draft: copying the fixtures or writing richer ones. The existing fixtures are deliberately partial (Pikachu has no abilities or genus), so each scenario uses the fixture that has what it asserts (Bulbasaur and Deoxys for US01 and for the sync of category and abilities, Pikachu and Eevee for the detail view and the evolution tree) and nothing was changed in `src/test/resources/pokeapi`.
- Mine, corrected on the way: a bash heredoc with accented text failed to parse (same as entries 007 to 009), so the files were written with the editor tool; the "local name over 100 characters" example had 92 characters, the one failure of the first run (the backend correctly answered 200), fixed to 101. No scenario was weakened and no backend bug was found.
- Left open: a re-sync inside the 6 h cache window returns the cached PokeAPI data, so "refreshes the PokeAPI fields" cannot be shown with changed upstream data without clearing the cache; the scenario therefore only proves that the local fields survive and the status is 200. Scenarios pay a 20 s Spring plus container start once per run (about 40 s for the whole suite).

---

## 011 — T8 Docker Compose (subagent)

**Prompt (summarised):** T8 from `docs/TASKS.md`: a frontend image (multi-stage Node build, nginx with SPA fallback and `/api` proxied to the backend so the browser is same-origin), and a compose file with `db`, `backend`, `frontend` (port 3000), health checks and `depends_on: condition: service_healthy`. The orchestrator added: build with an empty `VITE_API_URL`, a dev-only `JWT_SECRET` default plus `.env.example`, configurable host ports, a CSP that lets PokeAPI sprites load, and a check from a clean clone under a separate project name, without ever touching the existing `pgdata` volume.

**Output:**

- `frontend/Dockerfile` (`node:22-alpine` build with `ARG VITE_API_URL=""`, runtime `nginxinc/nginx-unprivileged:1.27-alpine`, non-root, port 8080 inside), `frontend/nginx.conf`, `frontend/security-headers.conf`, `frontend/.dockerignore`, `docker-compose.yml` (three services, health checks, `${VAR:-default}` for secrets and ports), `.env.example`.
- No application code was changed, so there was no red/green cycle: the verification is running the stack from a clean clone (details in the task report). Three commits `build(docker)`.

**Review notes:**

- Chosen, not defaulted: nginx resolves `backend` per request (`resolver 127.0.0.11` plus a variable in `proxy_pass`), so a recreated backend container does not leave nginx pointing at a dead IP; the security headers live in an included snippet because `add_header` is not inherited into a location that sets its own; `style-src 'self'` without `unsafe-inline` works because React writes `style` through the CSSOM (the stat bars rendered with their widths, no violation); the health checks use `127.0.0.1`, not `localhost`, to avoid an IPv6 mismatch in busybox `wget`; the build stage does not run `npm test` (the build already type-checks, tests belong to CI and the developer).
- Corrected after looking at real responses: the first nginx config duplicated `X-Frame-Options` and `X-Content-Type-Options` on proxied API responses (the backend sets them already), and sent two `Cache-Control` headers for hashed assets (`expires` plus `add_header`). Both fixed in follow-up commits. Rejected: proxying Swagger UI and the actuator through nginx, since the SPA's CSP would break Swagger's inline scripts and the actuator should not be public; they stay on `http://localhost:8080`.
- Left open: changing `POSTGRES_*` in `.env` after the volume exists does not change the existing database user; the compose file still publishes the database and backend ports for development convenience.

---

## 012 — Orchestration of T1–T11 (Claude Code, Opus as orchestrator)

**Prompt (summarised):** after the plan and the skeleton (entry 001), the owner asked an Opus session to complete every task in `docs/TASKS.md` by delegating each one to its own Sonnet subagent, strictly one after another in the order **T1 → T2 → T4 → T9 → T6 → T3 → T10 → T7 → T5 → T8 → T11**. Each subagent gets the "Rules for every agent" block, its task text verbatim and the TDD commit instructions (`[red]`, `[green]`, `refactor`). The orchestrator reviews the commits of each task for: framework-free `domain` and `application`, controllers on `port.in` interfaces only, services wired in `UseCaseConfig`, RFC 7807 errors, tests written first (visible in the commit order), no symlinks, no secrets, no unrelated rewrites of shared files. A task that is wrong goes back with concrete fixes. After each task the orchestrator runs `mvnw verify` (and the frontend build, lint and tests when the frontend changed) on `main`; after two failed fix rounds the task is reverted with `git revert`. Nothing is pushed. Done means: every task committed, everything green, the compose stack works in the browser with the demo login, the `genai/` section complete, README and presentation final, the AI log complete.

**What the orchestrator did beyond relaying the prompts:**

- Added environment and context notes to each prompt: Windows paths and PowerShell, Node 20.15 and the version pinning that follows from it, and facts from earlier tasks (the existing `ConflictException` and `Clock` bean, the fixed `V1__init.sql`, the existing JSON fixtures).
- Carried open issues forward so that the task owning the code fixed them: T2's stale-version 500 and its concurrent-first-sync 500 went to T3 (both are 409 now); T4's "an expired token gets 401 even on public routes" became a client rule in T6 (the token is attached only to protected calls); T4's missing `JWT_SECRET` in compose went to T8. T10 was warned not to create `src/test/resources/application.yml`, which would shadow the main configuration.
- Changed the T9 plan. Instead of one agent writing prompt, output and review, the prompt was written by the T9 agent, the output was generated by a separate fresh agent that saw only the prompt text (committed unedited as `3c049e0` by the orchestrator), and then reviewed by the T9 agent. The reviewer therefore did not write the code it criticises and could not plant flaws in it.
- Verified independently: re-ran `mvnw verify` and the frontend checks after every task, re-ran the `genai/corrected` tests, and did a manual browser pass of all flows on the Docker stack (browse, detail, register, login as demo, add to collection, edit with tag validation, a real 409 followed by Reload, delete with confirmation; no application errors or warnings in the console).

**Output:** the commits of T1 to T11 (entries 002 to 011, 013) and the log entries the subagents wrote themselves.

**Review notes:**

- No task needed to be sent back: all passed review in the first round, so the `git revert` path was never used.
- Said plainly: the review was done by reading the diffs and re-running the tests, not by a line-by-line audit of every test file. The corrections that did happen were found by running things (the subagents' own runs, the IT suites, the browser pass), and are listed in the entries above.

---

## 013 — T11 README and presentation (subagent)

**Prompt (summarised):** T11 from `docs/TASKS.md`: `README.md` (overview, features per story, stack, architecture diagram, run instructions for Docker and for local backend and frontend, demo credentials, API table, testing and coverage, caching, error format, decisions and trade-offs, next steps, links to `genai/` and the AI log), `docs/PRESENTATION.md` (problem, stories to endpoints, decisions and alternatives, TDD walkthrough with commit references, AI usage and checks, a 5-minute demo script, limitations) and `docs/architecture.md` (Mermaid). The orchestrator added: every claim must be verifiable in the repository (read the code, the plan, all log entries, compose and `.env.example`, `git log` for real hashes); do not invent numbers; for coverage either report test counts only, or add JaCoCo (add-only, no thresholds, separate commit) and a frontend coverage provider if it works cleanly; state the known limitations honestly; append entries 012 and 013.

**Output:**

- `build(coverage)` commits: JaCoCo 0.8.13 in `backend/pom.xml` (`prepare-agent`, and `report` in `post-integration-test` so surefire and failsafe, i.e. unit, slice, IT and Cucumber, share one `jacoco.exec`); `@vitest/coverage-v8` 3.2.7 (same version as Vitest), an `npm run test:coverage` script, a `coverage` block in `vite.config.ts`, and `coverage` added to the ESLint ignores. No thresholds. Measured: backend lines 96.7 % (810 of 838), branches 82.9 % (194 of 234), instructions 96.5 %, ITs and Cucumber included; frontend lines 97.8 %, branches 93.6 %, functions 97.2 %.
- `docs/architecture.md` with five Mermaid diagrams (layers and dependency direction as `ArchitectureTest` enforces them, Docker deployment, US03 sync sequence, US04 update sequence with both 409 paths, ER diagram of V1/V2).
- `README.md` (replaces the work-in-progress stub) and `docs/PRESENTATION.md`.
- Verified while writing: `mvnw -B verify` after adding JaCoCo (331 unit/web tests and 148 ITs, green), `npm run build`, `lint` and `test` (259 tests), both Docker images still build, per-class test counts from the surefire and failsafe reports (they add up to 331 and 76 + 72), the real 400 and 401 bodies and the real list/detail JSON from the running stack, and every commit hash cited.

**Review notes:**

- Chosen, not defaulted: measure coverage instead of writing "not measured". The report merges unit and integration runs, which is stated next to the numbers, and `clean verify` is the way to refresh them because the data file is appended to. The generated `frontend/coverage` folder made `npm run lint` print warnings for generated files; found by running lint, fixed by ignoring the folder.
- Rejected from my own drafts, caught by checking against the sources: an "other adapters and config" test count of 36 (summing the surefire reports gives 43, which also makes the total 331); an invented disk-space requirement for Docker; a demo step that said to "search the grid" for Eevee although the app has no search; "13 issues, 6 fixed" for the GenAI review (`review.md` says items 1 to 6 and the test gap in 9 were corrected, so 7 corrected and 6 left open); an update sequence that showed the client's version in the SQL `WHERE`, when the adapter writes with the version the service just read (the service has already compared the two), and a diagram note that referred to a message number that autonumbering would have shifted.
- Mermaid is only rendered by GitHub, which I cannot see from here, so every block in `README.md` and `docs/architecture.md` was parsed with the `mermaid` package under Node and jsdom (installed in a scratch folder outside the repository): all six parse. That proves the syntax, not the layout.
- Left open: the 5-minute demo script was written from the code and from the orchestrator's browser pass, not clicked through again here; the stack was not rebuilt from a clean clone after the coverage commits (the two images were built successfully, the running containers were left untouched); the coverage numbers are from one local run and there is no CI; the README says in plain words that the collection is global and that re-sync can return cached data.
