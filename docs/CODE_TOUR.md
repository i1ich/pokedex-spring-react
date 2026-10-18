# Code tour

A route through the repository for a screen-shared review: where each part lives, which file to open for each review criterion, and one request followed through every layer. Paths are relative to the repository root; the backend package root `backend/src/main/java/com/ilia/pokedex` is written as `…/pokedex`. Rationale is in the [README](../README.md) and [`PRESENTATION.md`](PRESENTATION.md), diagrams in [`architecture.md`](architecture.md).

**Contents:** [1. Map](#1-map) · [2. One request through every layer](#2-one-request-through-every-layer) · [3. Files per review criterion](#3-files-per-review-criterion) · [4. Frontend](#4-frontend) · [5. Commands to run live](#5-commands-to-run-live)

## 1. Map

```text
backend/src/main/java/com/ilia/pokedex/
  domain/model/          records and rules: LocalPokemon, PokemonIdentifier, PageQuery, User, PokemonDetails, ...
  domain/exception/      InvalidRequest, ResourceNotFound, Conflict, ExternalService, AuthenticationFailed
  application/port/in/   one interface per use case (ListPokemonUseCase, SyncPokemonUseCase, ...)
  application/port/out/  what the core needs from outside: PokemonCatalog, LocalPokemonRepository,
                         UserRepository, PasswordHasher, TokenIssuer
  application/service/   the use cases: plain Java, no Spring annotations
  adapter/in/web/        controllers, request/response DTOs, GlobalExceptionHandler, problem+json writers
  adapter/in/seed/       DemoDataSeeder (background copy of Pokémon 1-12)
  adapter/out/pokeapi/   PokeApiClient (HTTP + @Cacheable), PokeApiCatalogAdapter (mapping, fan-out)
  adapter/out/persistence/ JPA entities, Spring Data repositories, adapters implementing port.out
  adapter/out/security/  BCryptPasswordHasher, JwtTokenIssuer
  config/                UseCaseConfig (builds the services), SecurityConfig, PokeApiConfig, ...
backend/src/main/resources/db/migration/   V1__init.sql (schema), V2__seed_demo_user.sql
backend/src/test/                          tests mirror the main packages; features/ holds the Cucumber specs
frontend/src/                              api, auth, hooks, components, pages, lib (section 4)
genai/                                     the GenAI exercise: prompt, output, review, corrected
```

The dependency rule in one sentence: `adapter` and `config` may use `application` and `domain`; `application` uses only `domain`; nothing in `domain` or `application` imports Spring, JPA, servlet or Jackson. The rule is enforced by the test `backend/src/test/java/com/ilia/pokedex/architecture/ArchitectureTest.java`.

## 2. One request through every layer

US04, `PUT /api/v1/local-pokemon/25` with `{localName, region, notes, tags, version}`. Open the files in this order:

| Step | File | What to point at |
|---|---|---|
| 1. Security | `…/pokedex/config/SecurityConfig.java` | Public: `GET /api/v1/pokemon/**`, `POST /auth/register` and `/auth/login`, health, Swagger; every other request (all of `/api/v1/local-pokemon/**`, `/auth/me`) needs a JWT; 401/403 written as problem+json |
| 2. Controller | `…/pokedex/adapter/in/web/LocalPokemonController.java` | Depends on `UpdateLocalPokemonUseCase` (an interface), maps the DTO to a command, maps the result back |
| 3. Request shape | `…/pokedex/adapter/in/web/dto/UpdateLocalPokemonRequest.java` | Bean Validation sizes; an unknown or read-only property is rejected with its name (a throwing `@JsonAnySetter`) |
| 4. Use case port | `…/pokedex/application/port/in/UpdateLocalPokemonUseCase.java` | The contract the web layer sees |
| 5. Business rules | `…/pokedex/application/service/UpdateLocalPokemonService.java` | Rules in check order: id, version, trim, kebab-case tags, at most 10; 404 if missing; 409 if `version` differs |
| 6. Domain | `…/pokedex/domain/model/LocalPokemon.java` | `withProprietaryFields(...)` changes only the user's fields; PokeAPI fields stay |
| 7. Outbound port | `…/pokedex/application/port/out/LocalPokemonRepository.java` | What the service needs, nothing about JPA |
| 8. Adapter | `…/pokedex/adapter/out/persistence/JpaLocalPokemonRepositoryAdapter.java` | `saveAndFlush` with JPA `@Version`; a lost race becomes `ConflictException` (409), never a 500 |
| 9. Wiring | `…/pokedex/config/UseCaseConfig.java` | The only place that creates the service and hands it the adapter |
| 10. Errors | `…/pokedex/adapter/in/web/GlobalExceptionHandler.java` | Domain exception to status, once: 400, 404, 409, 502, 500 |

Tests for the same path, one per level: `UpdateLocalPokemonServiceTest` (34 cases, Mockito), `LocalPokemonModificationControllerTest` (web slice), `UpdateLocalPokemonIT` (real PostgreSQL, stale replay gives 409), `features/us04_local_modification.feature` (Cucumber).

The same walk for the other stories: US01 `PokemonController` → `ListPokemonService` → `PokeApiCatalogAdapter.findSlice/searchByName` → `PokeApiClient`; US02 `GetPokemonDetailsService` → `PokeApiCatalogAdapter.findDetails`; US03 `SyncPokemonService` (first sync, re-sync, unchanged re-sync saves nothing).

## 3. Files per review criterion

| Criterion | Open | Say |
|---|---|---|
| Clean Architecture | `ArchitectureTest.java`, `config/UseCaseConfig.java`, any `application/service/*Service.java` (no Spring imports) | The layering is a failing test, not a convention; services are plain classes |
| Separation of concerns | `PokeApiClient.java` next to `PokeApiCatalogAdapter.java` | HTTP and caching in one bean, mapping and fan-out in another (`@Cacheable` is bypassed on self-calls) |
| Testing and TDD | `git log --oneline` (the `[red]` / `[green]` pairs), `backend/src/test/resources/features/`, `UpdateLocalPokemonServiceTest.java` | Tests first, one rule at a time; counts and coverage in the README |
| Code quality | `UpdateLocalPokemonService.java` (Javadoc lists the rules in check order), `GlobalExceptionHandler.java`, `domain/model/PokemonIdentifier.java` | Small classes, domain exceptions, one error format |
| Functionality | The running app (demo script in `PRESENTATION.md` section 7), Swagger UI | Every story end to end; no console warnings (section 4) |
| Data layer | `db/migration/V1__init.sql`, `adapter/out/persistence/LocalPokemonEntity.java` | Flyway owns the schema, Hibernate only validates it; PK is the PokeAPI id |
| Auth | `application/service/AuthenticateUserService.java`, `adapter/out/security/JwtTokenIssuer.java` | Timing equalised for unknown users, BCrypt 72-byte limit, stateless JWT |
| Caching | `backend/src/main/resources/application.yml` (Caffeine spec), `adapter/out/pokeapi/PokeApiClient.java`, `PokeApiClientCachingTest.java` | Four Caffeine caches, 5000 entries, 6 h |
| GenAI | `genai/README.md`, `genai/review.md`, `docs/ai-log/prompts.md` | Prompt, unedited output, a review that ran the code, corrections test-first; every prompt used to build this project |

## 4. Frontend

```text
frontend/src/
  api/         client.ts (fetch wrapper, problem+json parsing, token only on protected calls),
               pokemon.ts, localPokemon.ts, auth.ts, queryKeys.ts, queryClient.ts, types.ts
  auth/        AuthContext.tsx, session.ts (token store usable outside React), ProtectedRoute.tsx
  hooks/       one hook per use case: usePokemonPage, usePokemonDetail, useSyncPokemon,
               useLocalPokemon, useUpdateLocalPokemon, useDeleteLocalPokemon
  components/  PokemonCard, Pagination, EvolutionTree, StatBars, TagInput, ConfirmDialog, ...
  pages/       BrowsePage, PokemonDetailPage, CollectionPage, EditPokemonPage, LoginPage, RegisterPage
  lib/         validation.ts (mirrors the server rules), format.ts, pagination.ts, returnTo.ts
  test/        setup.ts (fails any test that writes console.error or console.warn), fixtures, helpers
```

What to show: `hooks/useDeleteLocalPokemon.ts` (optimistic delete with rollback), `pages/EditPokemonPage.tsx` (the 409 banner and Reload), `components/PokemonCard.tsx` (sprite, then artwork, then "?"), `components/EvolutionTree.tsx` (nested lists, the viewed form at its species' node), `test/setup.ts` (the console rule). Server state lives in TanStack Query; React Context holds only the session.

## 5. Commands to run live

```bash
docker compose up --build                 # the whole stack: http://localhost:3000, Swagger on :8080
cd backend && ./mvnw test                 # 344 tests, no Docker (Windows: .\mvnw.cmd test)
cd backend && ./mvnw verify               # + 76 ITs and 75 Cucumber scenarios on Testcontainers, about 2.5 min
cd frontend && npm test                   # 284 tests
git log --oneline --grep="\[red\]"        # the test-first commits
```

Tip for the review: run `verify` once before the call and keep the output, because it takes a few minutes; run `mvnw test` live if asked.
