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
