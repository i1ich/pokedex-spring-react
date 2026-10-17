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
