# Presentation: how I approached the exercise

This is the "thought process" document. It explains what I understood, what I decided and why, how the code was written test-first, how AI tools were used and checked, and a 5-minute demo script. Every claim points at something in the repository (commit hashes are real; `git show <hash>` works). Setup and reference material are in the [README](../README.md); diagrams are in [`architecture.md`](architecture.md).

**Contents:** [1. The problem](#1-the-problem-as-i-understood-it) · [2. Stories to endpoints](#2-user-stories-to-endpoints) · [3. Architecture decisions](#3-architecture-decisions-and-alternatives) · [4. TDD walkthrough](#4-tdd-walkthrough) · [5. How AI was used](#5-how-ai-was-used-and-how-it-was-checked) · [6. After T11](#6-after-t11-what-using-the-app-changed) · [7. Demo script](#7-demo-script-5-minutes) · [8. Known limitations](#8-known-limitations) · [9. Questions I expect](#9-questions-i-expect)

## 1. The problem as I understood it

The exercise (summarised in [`PLAN.md`](PLAN.md)): build a full-stack Pokédex on top of the public PokeAPI, in Java with Spring Boot and PostgreSQL, with a React frontend, delivered so that it starts with one command. Four user stories:

| Story | Need |
|---|---|
| US01 Enumeration | A paginated list of Pokémon with image, name, id, category, weight and abilities |
| US02 Detailed view | One Pokémon in detail: image, stats, description, evolution chain |
| US03 Data synchronisation | Copy a Pokémon from PokeAPI into the application's own database |
| US04 Local modification | Change and remove data in that local copy, with proprietary fields PokeAPI does not have |

Around them: a second collection for **user management** (so authentication, public versus protected routes), caching of the external API, and a mandatory **GenAI section** where an AI tool's output is generated, reviewed critically and corrected. Quality signals the plan aims at: tests with visible TDD evidence, clean structure, documentation, Docker.

How I read the ambiguous parts, and what each reading cost or bought:

- **"Create / Update / Delete" against PokeAPI data.** PokeAPI is read-only, so US03 is the "create" (an idempotent copy into PostgreSQL) and US04 is update/delete on that copy. The PokeAPI-owned fields (name, stats, sprite) are read-only in my API; the user owns `localName`, `region`, `notes` and `tags`. That split is what makes a re-sync safe: it overwrites only the columns PokeAPI owns.
- **"Category".** It is not on the Pokémon resource; it is the English `genus` of the species ("Seed Pokémon"), reached through the `species` link, not by name (form names like `deoxys-normal` are not species names).
- **"Secondary collection for user management".** `app_user` with BCrypt hashes and a role. I kept it small: register, login, `/me`.
- **What is public.** Browsing and details are public; anything that touches the local collection needs a token.
- **Time.** Deadline Monday 19 Oct 2026, 18:00 (UTC-3); the plan targets Monday 15:00 with a buffer for a final clean-machine `docker compose up`.

## 2. User stories to endpoints

| Story | Endpoint | Auth | Rules that mattered |
|---|---|---|---|
| US01 | `GET /api/v1/pokemon?page&size&q` | public | `size` 1 to 50 (protects PokeAPI), zero-based `page`, optional `q` = name contains (PokeAPI cannot filter, so the cached list of all names is filtered here and only the shown page is enriched); 1 list call + 2 calls per Pokémon, in parallel; cached |
| US02 | `GET /api/v1/pokemon/{idOrName}` | public | id/name `[a-z0-9-]{1,100}` (else 400); evolution chain is a **tree** (Eevee has eight children); missing species or chain degrades to a one-node chain instead of failing the page |
| US03 | `POST /api/v1/local-pokemon/sync` | token | 201 + `Location` for new, 200 for a re-sync; re-sync keeps `localName`, `region`, `notes`, `tags`; unknown upstream is 404, upstream down is 502 |
| US03 (read side) | `GET /api/v1/local-pokemon`, `/{id}` | token | the frontend needs a list and a single read |
| US04 | `PUT /api/v1/local-pokemon/{id}` | token | five-property body incl. `version`; 400 for shape, limits, kebab tags, unknown or read-only property; 404; **409 on a stale version** |
| US04 | `DELETE /api/v1/local-pokemon/{id}` | token | 204 / 404 |
| Users | `POST /auth/register`, `POST /auth/login`, `GET /auth/me` | public / public / token | username normalised, password 8 to 72 bytes, one 401 message for unknown user and wrong password |

Cross-cutting: errors are `application/problem+json` with a `field`/`message` list on 400 (see README); pagination is `{items, page, size, totalElements, totalPages}`.

## 3. Architecture decisions and alternatives

Full diagrams: [`architecture.md`](architecture.md).

| Decision | What I chose | Alternatives I weighed | Why this one |
|---|---|---|---|
| Structure | Clean Architecture (ports and adapters) in **one Maven module**, packages `domain`, `application`, `adapter.in/out`, `config` | Plain controller-service-repository; several Maven modules | The stories have real rules (identifier, paging caps, tag normalisation, version checks) worth testing without Spring. One module keeps the build simple; ArchUnit gives the enforcement a module boundary would |
| Enforcement | `ArchitectureTest`: layering, framework-free core, controllers use `port.in` interfaces, no `adapter.in` to `adapter.out` | Code review only | A violation fails the build, so it cannot erode while several agents work in parallel |
| Wiring | Services are plain classes built in `UseCaseConfig` | `@Service` everywhere | Keeps `application` free of Spring annotations (and checked) |
| Cache | Caffeine via Spring Cache, in a separate bean from the mapping adapter | Redis; no cache | Data is static and the app is single-instance; Redis is another container for no gain. `@Cacheable` is bypassed by self-invocation, hence two beans |
| Fan-out | `CompletableFuture` on a virtual-thread executor | Sequential calls; WebFlux | 40 independent calls per page; Java 21 virtual threads need no reactive stack |
| Local store | PostgreSQL, PK = PokeAPI id, Flyway, `ddl-auto: validate` | Document store; generated keys + unique constraint | Re-sync becomes an upsert; the schema is versioned and validated against the entities |
| Concurrency | `@Version` plus an explicit version check; both end as a 409 | Last write wins; pessimistic locks | Two users editing must not overwrite each other silently (US04). The service check gives a readable message, JPA closes the race |
| Auth | Stateless HS256 JWT (1 h), BCrypt, Spring's resource-server decoder | Server sessions; a custom filter | Stateless suits an SPA behind a proxy; the decoder is standard and tested. The cost (no revocation) is documented |
| Errors | One `GlobalExceptionHandler` and domain exceptions; the security chain writes the same body | Per-controller handling | One shape for the frontend to parse |
| Tests | Testcontainers PostgreSQL, not H2 | H2 in PostgreSQL mode | Migrations, `ON DELETE CASCADE` and optimistic locking run on the real database |
| Acceptance | Cucumber features per story, WireMock for PokeAPI, RestAssured | More MockMvc tests | The features read like the stories and exercise the whole HTTP stack without internet |
| Frontend state | TanStack Query for server state, Context only for auth, token in a tiny session store | Redux; everything in Context | Caching, invalidation and optimistic delete come for free; the store lets a 401 sign the user out from plain code |
| Delivery | nginx serves the build and proxies `/api` | Browser calls the backend directly (CORS) | Same origin in Docker, no API URL baked into the image |

## 4. TDD walkthrough

The rule for every unit of work: failing test first, minimal implementation, then refactor, committed in that order with `[red]` / `[green]` suffixes. A red commit contains the tests (and fixtures) plus compile-level stubs that throw `UnsupportedOperationException`, so the tests fail on behaviour, not on compilation. Read any pair with `git show <red>` then `git show <green>`.

| Unit | Red (tests) | Green (implementation) | Size (red / green) | Evidence of the red state |
|---|---|---|---|---|
| US01 list, cache, architecture rules | `ba9c7a5` | `3f417a0` | 18 files, +894 / 32 files, +1407 | architecture rules added with the first tests |
| US02 details | `b2f3b7e` | `dbac8c1` | 23 files, +967 / 7 files, +184 | red holds tests, fixtures (Pikachu, Eevee, Ditto) and throwing stubs; the green diff is small because the tests already pin the behaviour |
| US03 sync | `3cb9698` (after refactor `523e9c0`) | `76fc9a3` | 20 files, +1157 / 13 files, +413 | the identifier and paging rules were first extracted into the domain (`523e9c0`) so US01/US02 tests stay green and the new services reuse them |
| Auth | `8a09010` | `4496379` | 33 files, +1681 / 23 files, +669 | |
| US04 update/delete | `062730d` | `36f8b95` | 15 files, +1141 / 11 files, +307 | 34 service cases, 31 controller cases, stale-version IT; after green: 306 unit/web + 35 ITs passing |
| OpenAPI | `e74282b` | `bee0cf4` | 1 file, +192 / 8 files, +244 | an IT that reads the generated document: paths, bearer scheme, security on protected operations only, documented error responses |
| Demo seeder | `6410568` | `ba5c092` | 6 files, +401 / 7 files, +320 | |
| Frontend scaffold | `aa2cda4` (after `40e73ac`) | `8cb76cb` | 16 files, +1175 / 32 files, +1391 | 110 tests failing for missing modules |
| Frontend validation and API | `8d2cbd9` | `16cb115` | | |
| Frontend detail page | `8fa7bde` | `740c5a1` | | |
| Frontend login/register | `f8f1f9c` | `85b7d08` | | |
| Frontend collection, edit, 409 | `2ad68cc` | `78deab0` | 4 files, +799 / 13 files, +942 | |
| Frontend bug found in the browser | `86ebf36` | `3195f31` | | a rejected tag left a stray comma in the input; reproduced as a failing test before the fix |
| GenAI corrections | `89f191e` | `c7caa69` | 8 files, +558 / 10 files, +72 | 62 unit/web tests, 16 failing, all in the new tests that expose AI flaws; green: 61 unit/web + 11 ITs |

Outside the red/green cycle, with honest labels: the Maven wrapper and Testcontainers bumps (`d501fb2`, `acf3704`), the Cucumber infrastructure (`dead087`) and scenarios (`7464d83`, which are tests on already-working code and found no backend bug), the Docker work (`0244d82`, `79317ba`, `80129d2`: no application code, verified by running the stack) and the coverage tooling (`814a6c9`, `9b500ed`).

What the tests caught that review might not have:

- **A timing side channel (auth).** "Same message for unknown user and wrong password" was not enough: an unknown user skipped the BCrypt comparison and answered faster. The login service now compares against a throw-away hash, and a unit test pins it.
- **A strict-stubs failure** (`PotentialStubbingProblem`) showed the test did not stub that second comparison; fixed by stubbing it, not by loosening Mockito.
- **A lost update that cannot be a 500.** Updating a record another request deleted does not re-insert it; the JPA merge fails with an optimistic-locking error that the adapter turns into a 409. Covered by an IT.
- **A wrong example in my own test.** The Cucumber "name over 100 characters" case used 92 characters; the backend correctly answered 200, which is how the test failed. The example was fixed, not the rule.
- **Console noise.** The frontend test setup fails any test that writes to `console.error` or `console.warn`, which turns "no warnings in the browser console" (React keys, `act()`, router) into an enforced rule.

Totals now (see README for the table, measured on 18 Oct 2026 after the last commit): 344 backend tests with `mvn test`, 151 more with `mvn verify` (76 JUnit integration tests and 75 Cucumber scenarios), 284 frontend tests. Backend line coverage 96.8 % and branch coverage 81.7 % (unit, IT and Cucumber merged), frontend lines 97.9 % and branches 93.8 %. These are by-products, measured with tooling added in the last commits, not targets.

## 5. How AI was used and how it was checked

### Setup

The whole application was built with **Claude Code**. An **orchestrator session (Claude Opus)** read [`PLAN.md`](PLAN.md) and [`TASKS.md`](TASKS.md) and delegated each task, T1 to T11, to **one Sonnet subagent**, strictly one after another, in this order: **T1 → T2 → T4 → T9 → T6 → T3 → T10 → T7 → T5 → T8 → T11**. Each subagent received the shared "Rules for every agent" block, its task text verbatim, TDD commit instructions, and a few environment notes from the orchestrator (Windows paths, Node 20.15 version pinning, facts from earlier tasks such as the existing `ConflictException` and `Clock` bean). Every subagent had to append its own entry to [`docs/ai-log/prompts.md`](ai-log/prompts.md): the prompt, the output, and what it rejected or corrected.

| Task | Result | Log entry | Main commits |
|---|---|---|---|
| (planning) | `PLAN.md`, `TASKS.md`, skeleton, US01 TDD cycle | 001 | `ba9c7a5`, `3f417a0` |
| T1 US02 | details endpoint with evolution tree | 002 | `b2f3b7e`, `dbac8c1` |
| T2 US03 | persistence, sync, local reads | 003 | `523e9c0`, `3cb9698`, `76fc9a3` |
| T4 auth | register, login, JWT, problem+json 401/403, demo user | 004 | `8a09010`, `4496379` |
| T9 GenAI | prompt, unedited output, review, corrected code | 005 | `1cb8622`, `3c049e0`, `89f191e`, `c7caa69`, `196f225` |
| T6 frontend scaffold | API client, auth context, Browse page | 006 | `40e73ac`, `aa2cda4`, `8cb76cb` |
| T3 US04 | update (optimistic locking) and delete | 007 | `062730d`, `36f8b95` |
| T10 OpenAPI + seed | springdoc, background demo seeder | 008 | `e74282b`, `bee0cf4`, `6410568`, `ba5c092` |
| T7 frontend pages | detail, auth forms, collection, edit | 009 | `8d2cbd9` to `3195f31` |
| T5 Cucumber | 72 scenarios over real HTTP | 010 | `dead087`, `7464d83` |
| T8 Docker Compose | frontend image, full stack, health checks | 011 | `0244d82`, `79317ba`, `80129d2` |
| T11 docs | README, this document, diagrams, coverage tooling | 012, 013 | `814a6c9` to the final docs commits |

### What the orchestrator did beyond relaying prompts

- **Reviewed each task's commits** for: a framework-free `domain` and `application`, controllers on `port.in` interfaces, services wired in `UseCaseConfig`, RFC 7807 errors, tests written first (commit order), a test for each acceptance rule, no symlinks or secrets, no unrelated rewrites of shared files.
- **Re-ran `mvnw verify` on `main` after every task**, plus the frontend build, lint and tests, and re-ran the `genai/corrected` tests. After two failed fix rounds a task would have been reverted with `git revert`; it never came to that. Nothing was pushed.
- **Carried open issues forward** between tasks so they were fixed by whoever owned the code: T2's stale-version 500 and concurrent-first-sync 500 went to T3 (both are 409 now); T4's "401 for an expired token even on public routes" became a client rule in T6 (the token is only attached to protected calls); T4's missing `JWT_SECRET` in compose went to T8; T10 was warned not to create `src/test/resources/application.yml` (it would shadow the main config).
- **Changed the GenAI process** (T9): instead of one agent writing prompt, output and review, the prompt was written by one agent, the output generated by a separate fresh agent that saw only the prompt text (committed unedited, `3c049e0`), and then reviewed by the first agent. The reviewer did not write the code it criticises and could not plant flaws.
- **Checked the result as a user:** a manual pass through the browser on the Docker stack (browse, detail, register, login as demo, add to collection, edit with tag validation, a real 409 followed by Reload, delete with confirmation); the console showed no application errors or warnings, only Chrome's own network line for the deliberate 409.
- No task had to be sent back; all passed on the first round. That review was by reading diffs and re-running tests, **not a line-by-line audit** of every test file.

### What the AI got wrong or I changed (selected, all from the log)

| Where | Suggestion or first draft | What I did instead, and why |
|---|---|---|
| T3 | `@JsonIgnoreProperties(ignoreUnknown = false)` to reject read-only fields in `PUT` | Rejected: Spring Boot turns `FAIL_ON_UNKNOWN_PROPERTIES` off and a `false` annotation only defers to the mapper, so it rejects nothing. A `@JsonAnySetter` that throws keeps the strictness on that one DTO. Found by running it |
| T3 | `deleteById` plus `existsById` | A single `DELETE ... WHERE id = ?` returning the row count: one round trip, no check-then-act race |
| T4 | One message for unknown user and wrong password | Not enough: response time still differs. Added the throw-away hash comparison |
| T4 | Password rule "at least 8" only | Added 72 bytes maximum: BCrypt silently ignores everything after the 72nd byte |
| T2 | `long version` in the domain | Nullable `Long`: a primitive makes Spring Data treat every record with an assigned id as existing and `merge` a row that is not there |
| T1 | Flatten the evolution chain | A tree, so Eevee keeps all eight branches |
| T10 | Repeat `@ApiResponse(content = ...)` on every method | Composed annotations (`@ProblemResponses.NotFound`), which springdoc reads as meta-annotations; the IT proves it. springdoc also drops the implicit 200 when any `@ApiResponse` is present, found by running it and now pinned by a test |
| T10 | Seeding in a synchronous runner | A daemon thread: with PokeAPI down, 12 ids at a 3 s connect timeout would block start-up for over half a minute |
| T7 | `<dialog>` with `showModal()` / `window.confirm` | A hand-rolled `alertdialog`: testable in jsdom and just as accessible (focus on Cancel, Tab trap, Escape) |
| T6 | `jsdom` latest | Pinned 26: 30 fails on Node 20.15 with `ERR_REQUIRE_ESM`. Also a file named `authContext.ts` next to `AuthContext.tsx` resolved wrongly on case-insensitive Windows; renamed `context.ts` |
| T8 | nginx config with duplicated security headers on proxied API responses and two `Cache-Control` headers on hashed assets | Fixed in `79317ba` and `80129d2` after looking at real responses; Swagger UI and the actuator are deliberately not proxied (the SPA's CSP would break Swagger) |
| T5 | Richer copies of the fixtures | Reused the existing deliberately partial fixtures; each scenario uses the one that has what it asserts |
| T9 | The AI output's claim of "complete, compilable" code | It did not compile its tests (a package-private class used across packages); "43 passing tests" is true only after a one-word fix |
| All | Multi-line edits through shell heredocs | Quoting and accents mangled them in PowerShell/bash several times; files were written with the editor tool and the result checked |

### The GenAI section

[`genai/`](../genai/README.md) holds the exercise: the prompt, the AI output exactly as received, the review ([`review.md`](../genai/review.md)) and the corrected project. Highlights of the review:

- It was **run**, not just read. As delivered, the test code does not compile. With a one-word fix its 43 tests pass.
- 13 issues were logged. Fixed test-first (62 unit/web tests at the red commit, 16 failing): an unsatisfiable `Accept` header returned 500, `status` accepted as an enum ordinal, `due_date` with a time part silently truncated, the "not in the past" rule implemented twice with the host clock and zone, the page-size cap only in the controller. Also added: the missing database-level tests (11 ITs) that show ownership holds in PostgreSQL.
- Six issues were **documented and left open on purpose** (lost updates on PUT, no 401 body check, a 409 for a user missing from `users`, scalar coercion, silent unknown properties, audit timestamps not from the `Clock`) with the reason.
- The review also lists what the AI got **right** (ownership with 404 for foreign tasks, DTO separation, problem+json, Flyway plus `validate`), so it does not invent flaws.

### Limits of how I used AI

AI wrote nearly all the code and tests; my part was the specification, the decomposition into tasks, the architectural rules that make mistakes visible (ArchUnit, red-before-green commits, tests that fail on console noise), the review of each result and the final manual check. Where a claim could not be checked by running something, it is flagged as open in the log rather than asserted.

## 6. After T11: what using the app changed

After the documentation task (T11) I used the running Docker stack the way a visitor would, in the browser, and changed what did not hold up. The work was done with Claude Code (the commits are co-authored by Claude Sonnet 5.5), one small commit per finding, with `mvnw verify` and the frontend checks green after each. Every behaviour change has tests; `9b6fb7c`, `64849b5` and `e11e9f1` were written red-first. Log entry 014 in [`ai-log/prompts.md`](ai-log/prompts.md) has the detail.

| What I noticed | Why it mattered | Change | Commit |
|---|---|---|---|
| Someone pressing "Add to collection" on a Pokémon already stored made another user's open edit form fail with 409 | A re-sync saved the record and bumped `version` even when PokeAPI data had not changed, so a conflict appeared out of nowhere | A re-sync with unchanged data saves nothing (`LocalPokemon` decides, `SyncPokemonServiceTest` pins it) | `94b8006` |
| Register hints talked about "72 bytes" and "3–50 characters" | Internal limits confuse a visitor; the server still enforces them | Plain-language hints; the demo login is shown under the login form | `94b8006` |
| Clicking Maushold, Oinkologne or Toxtricity in an evolution tree gave 404 | Chains link species names, but `/pokemon/{name}` only knows form names (`maushold-family-of-four`) | A miss is retried through the species' default variety | `230719d` |
| Opening `pikachu-gmax` showed a tree without the Pokémon being viewed | The tree is built from species; forms are not evolutions | Details carry `speciesName`; the viewed form is shown at its species' node | `230719d` |
| Some cards were blank (`zygarde-mega` has a broken sprite upstream) | US01 asks for a picture on every card | Each failed image load moves to the next candidate, then a "?" placeholder | `230719d`, `9b6fb7c` |
| "Back to Browse" always returned to page 1 | Losing your place in 1,300 entries | The link returns to the page and search you came from | `230719d` |
| Hard to reach page 40 of the catalogue | Only Previous / Next | A "Go to page" field, clamped to 1..totalPages | `9b6fb7c` |
| No way to find a Pokémon without paging | The catalogue is large | Search by part of a name: `q` on the API (the cached list of all names is filtered, only the shown page is enriched), a search box with `?q=` in the URL | `014d86e` |
| It was not obvious that the collection is shared | A delete removes the record for everyone | A note on the collection page and in the delete dialog | `64849b5`, `e11e9f1` |

Two changes were tried and undone on purpose: official artwork as the first picture in the grid (`9b6fb7c`) went back to sprite-first because US01 asks for the sprite (`1ecee7e`), and a faint Poké Ball placeholder (`0678bad`) was reverted in favour of the plain "?" (`3b28ba8`).

## 7. Demo script (5 minutes)

Before: `docker compose up --build` has finished, `docker compose ps` shows three healthy services, the browser is on http://localhost:3000, a second tab is ready. The database starts with Pokémon #1 to #12 in "My collection" (background seeder); Pikachu (#25) is not in it.

| Time | Do | Say |
|---|---|---|
| 0:00 | Terminal: `docker compose ps`. | One command, three containers: PostgreSQL, Spring Boot API, nginx with the React build. All healthy |
| 0:30 | **Browse.** Scroll the grid; click **Next**; note `?page=2` in the URL; press reload. | US01: paginated, 20 per page, page lives in the URL. The first uncached page calls PokeAPI in parallel; from here it is served from the Caffeine cache |
| 1:15 | Open **Eevee** (type `eevee` in the search box). Show artwork, stat meters, description; in the evolution tree click **Vaporeon**. | US02: stats, English description, evolution as a tree (eight branches), every node is a link |
| 2:00 | Go to **Pikachu**, click **Add to my collection** while logged out. You land on **Login**; log in as `demo` / `demo12345`; you are returned to Pikachu; click **Add** again, then once more. | US03: protected, so anonymous users are sent to log in and come back. First click answers 201 "Added to your collection", the second 200 "Already in your collection". Idempotent |
| 2:45 | Click **Edit your copy**. Local name `Sparky`, region `Kanto`, tags: type `Bad Tag` + Enter (rejected, stays in the field), then `electric`, `starter`. **Save changes.** | US04: client-side validation mirrors the server rules (lowercase kebab tags, limits); the server enforces them again. PokeAPI fields are shown read-only |
| 3:30 | Open the edit page of Pikachu in two tabs. Save a change in tab 2. In tab 1 change the notes and **Save**: the banner "This Pokémon was edited elsewhere" appears and Save is disabled. Click **Reload**, then save. | Optimistic locking: tab 1 sent an old `version`, so the server answered 409 problem+json instead of overwriting. Reload refetches and restarts the form from the new record |
| 4:15 | **My collection**: **Delete** on Pikachu, dialog opens with focus on Cancel; press Esc, open again, confirm. | Delete is optimistic with rollback, behind a confirmation dialog (keyboard accessible) |
| 4:30 | http://localhost:8080/swagger-ui.html: open `GET /api/v1/local-pokemon` and **Try it out** without authorizing: 401 `application/problem+json`. Then show `docs/architecture.md` or run `.\mvnw.cmd verify` output from before. | Same error shape everywhere, including the security filter chain. 344 + 151 backend tests, 284 frontend tests, ArchUnit keeps the layers honest; the GenAI review is in `genai/` |

If PokeAPI is slow during the demo, reload once: the second request is cached. If the live conflict is fiddly, the same 409 is produced by `PUT` with an old `version` from Swagger UI after authorizing with a token from `/auth/login`.

## 8. Known limitations

Details and the reasoning are in the README ([limitations](../README.md#known-limitations), [next steps](../README.md#what-i-would-do-next)). The ones I would raise first:

1. **The collection is shared, not per user.** The brief describes the local store as one replica of PokeAPI data (US03) and users only for authentication, so `local_pokemon` has no owner column, as designed in `PLAN.md`; every signed-in user sees and edits the same records, and the UI says so. Per-user ownership is the first thing I would add if the product needed it.
2. **Re-sync can return cached data** inside the 6 h cache window, so "refreshed from PokeAPI" is only as fresh as the cache.
3. **The seeder re-adds** #1 to #12 on the next restart if a user deleted them (`SEED_ENABLED=false`).
4. **Auth is simple:** demo credentials live in a normal Flyway migration; no refresh token or revocation (1 h TTL); the backend answers 401 for an expired token even on public routes, so the client only sends the token to protected calls.
5. **Version in the body**, not `ETag`/`If-Match`; Jackson coerces scalars (`"localName": 123` is stored as `"123"`).
6. **The first uncached Browse page is slow-ish** (1 list call + 2 calls per Pokémon).
7. **Not done:** CI, Playwright end-to-end tests, a screen-reader pass, filters by type or tag.

## 9. Questions I expect

- **Why not store everything locally and skip the cache?** The catalogue is over 1,300 entries of static data (1351 when I checked); copying it all would need a bulk import, and US03 is explicitly about copying a Pokémon on request. The cache gives fast browsing without owning the data.
- **Why does a stale edit return 409 rather than merging?** The user's fields are replaced as a whole; merging would hide a conflict the user should see. The UI offers Reload.
- **Why two version checks?** The service check gives a clear message and fails before touching the store; JPA `@Version` is the only thing that is atomic with the write.
- **Is the AI-written code trustworthy?** Only as far as it was checked: every task ended with a full `verify` on `main` by someone other than the author, the architecture rules are tests, and the GenAI review shows what a careful run finds (code that did not compile, several behaviour bugs, a rule implemented twice).
- **Why are Gigantamax, Mega or gender forms not in the evolution tree?** PokeAPI's evolution chain links species, and those forms are varieties of one species, not evolutions (Gigantamax and Mega are temporary battle transformations). Nidoran ♀ and ♂ are the exception that proves it: two species, two chains. The tree shows the form you opened at its species' node; a list of a species' forms on the detail page would be a natural addition.
- **What would you do differently with more time?** Per-user ownership first, then `ETag`, refresh tokens and CI (README, "What I would do next").
