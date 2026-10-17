# Task Breakdown for Parallel Agents

Each task is small, has a clear owner set of files, and ends with green tests. Launch the tasks of one wave in parallel; start the next wave after the previous one is merged.

| Wave | Tasks | Depends on |
|---|---|---|
| A | T1 US02 detail · T2 persistence + US03 sync · T4 auth · T6 frontend scaffold · T9 GenAI section | skeleton (done) |
| B | T3 US04 + local CRUD · T7 frontend pages · T10 OpenAPI + seed data | T2, T4, T6 |
| C | T5 Cucumber acceptance tests · T8 full Docker Compose · T11 README + presentation | everything |

## Rules for every agent (paste together with the task prompt)

```text
You are working in the repository pokedex-spring-react (Java 21, Spring Boot 3.5, PostgreSQL, React + TS + Vite).
Read docs/PLAN.md first: it defines the architecture, endpoints, error format and data model. Do not change them without saying so.

Architecture rules (enforced by backend/src/test/java/.../architecture/ArchitectureTest.java):
- domain and application packages are pure Java: no Spring, JPA, Jackson or servlet imports.
- Controllers depend on use-case interfaces in application.port.in, never on services or outbound adapters.
- Services are created in config/UseCaseConfig.java, not annotated with @Service.
- Outbound adapters implement interfaces from application.port.out.

Process:
- TDD: write the failing test first, then the minimal implementation, then refactor.
  Commit in that order: "test(<scope>): ... [red]", "feat(<scope>): ... [green]", "refactor(<scope>): ...".
- Unit tests: JUnit 5 + Mockito + AssertJ. Web: @WebMvcTest + MockMvc + @MockitoBean.
  PokeAPI: MockRestServiceServer with JSON fixtures in src/test/resources/pokeapi. DB: Testcontainers, class name *IT.
- Errors: throw domain exceptions (InvalidRequestException, ResourceNotFoundException, ...); the
  GlobalExceptionHandler maps them to application/problem+json. Add a new exception type only if no existing one fits.
- Shared files (pom.xml, application.yml, UseCaseConfig.java, GlobalExceptionHandler.java, V-migrations):
  ADD to them, never rewrite or reorder existing content. New Flyway migrations use the next free version number.
- Run `cd backend && mvn verify` (or `mvn test` if Docker is unavailable) before finishing; all tests must pass.
- Do not add symlinks. Do not commit secrets.
- Finish with a short report: files changed, tests added, decisions made, anything left open.
- Append an entry to docs/ai-log/prompts.md: the prompt you received (summarised), what you produced,
  and any AI suggestion you rejected or corrected and why.
```

---

## T1 — US02 Detailed view (backend)

**Goal:** `GET /api/v1/pokemon/{idOrName}` returns image, core stats, narrative description and evolution chain.

**Owns:** `domain/model/PokemonDetails.java` (+ nested records), `application/port/in/GetPokemonDetailsUseCase.java`, `application/service/GetPokemonDetailsService.java`, new methods in `PokemonCatalog` + `PokeApiCatalogAdapter` + `PokeApiClient`, new DTOs under `adapter/out/pokeapi/dto`, `adapter/in/web/dto/PokemonDetailsResponse.java`, a new handler method in `PokemonController`.

**Details:**
- Image: `sprites.other["official-artwork"].front_default`, fallback `sprites.front_default`.
- Stats: list of `{name, baseStat}` (hp, attack, defense, special-attack, special-defense, speed); also height (m) and weight (kg), types.
- Description: first English `flavor_text_entries` of the species; normalise whitespace (`\n`, `\f` → space).
- Evolution chain: species → `evolution_chain.url` → `GET /evolution-chain/{id}`; flatten to an ordered list of `{id, name, minLevel, trigger}`; keep branches (Eevee) as multiple children, e.g. `{name, evolvesTo: [...]}` tree.
- New cached client methods: `fetchEvolutionChain(int id)` with cache `pokeapi-evolution` (add to `spring.cache.cache-names`).
- `idOrName` validation in the service: lowercase letters, digits and `-`, 1–100 chars, else `InvalidRequestException` (400). Unknown → 404.

**Acceptance:** service unit tests (valid, invalid id, not found propagation); adapter tests with fixtures for `pikachu`, `eevee` (branching chain) and one Pokémon with no evolutions; controller tests for 200/400/404/502.

---

## T2 — Persistence + US03 Data synchronisation (backend)

**Goal:** copy a Pokémon from PokeAPI into `local_pokemon` (schema in `V1__init.sql`), idempotently.

**Owns:** `domain/model/LocalPokemon.java`, `application/port/in/SyncPokemonUseCase.java`, `application/port/out/LocalPokemonRepository.java`, `application/service/SyncPokemonService.java`, `adapter/out/persistence/*` (JPA entities, Spring Data repositories, `JpaLocalPokemonRepositoryAdapter`), `adapter/in/web/LocalPokemonController.java` (sync endpoint only), new `PokemonCatalog` method `findByIdOrName` (coordinate with T1: if T1 already added a details lookup, reuse it).

**Details:**
- `POST /api/v1/local-pokemon/sync` body `{ "idOrName": "pikachu" }` → `201 Created` + `Location: /api/v1/local-pokemon/25` + body; re-sync of an existing record → `200 OK`, refreshes PokeAPI fields, **keeps proprietary fields** (`localName`, `region`, `notes`, `tags`).
- Also `GET /api/v1/local-pokemon?page&size` and `GET /api/v1/local-pokemon/{id}` (needed by the frontend; 404 if absent).
- `LocalPokemon` is a domain record (no JPA annotations). JPA entity lives in the adapter; map both ways.
- Use `@Version` for optimistic locking (column `version`), needed by T3.
- Do not secure endpoints here; T4 adds security.

**Acceptance:** service unit tests (new vs re-sync, proprietary fields preserved, invalid input, not found upstream); `JpaLocalPokemonRepositoryAdapterIT` on Testcontainers; controller tests for 201/200/400/404.

---

## T3 — US04 Local modification + full CRUD (backend) — wave B, after T2 and T4

**Goal:** update and delete local records with robust validation.

**Owns:** `UpdateLocalPokemonUseCase`, `DeleteLocalPokemonUseCase`, their services, request DTO `UpdateLocalPokemonRequest`, new handlers in `LocalPokemonController`, `ConflictException` (→ 409) in `domain/exception` + handler method.

**Details:**
- `PUT /api/v1/local-pokemon/{id}` body `{ localName, region, notes, tags[], version }`.
  - 404 if the record does not exist; 400 for malformed JSON, non-numeric id, `id < 1`, blank `localName` when present, length limits (localName ≤ 100, region ≤ 100, notes ≤ 1000, tag ≤ 50 chars, ≤ 10 tags, tags lowercase-kebab), missing `version`.
  - 409 when `version` does not match (stale update); PokeAPI-owned fields (name, stats, sprite) are read-only — unknown or read-only fields in the body → 400.
- `DELETE /api/v1/local-pokemon/{id}` → 204, 404 if absent.
- Bean Validation on the request DTO for shape; business rules (tags normalisation, uniqueness) in the service.

**Acceptance:** service tests for every rule; controller tests for 200/400/404/409/401; one `*IT` covering the stale-version 409 against PostgreSQL.

---

## T4 — Authentication and protected routes (backend)

**Goal:** register/login with JWT; public vs protected routes.

**Owns:** `domain/model/User.java`, `application/port/in/{RegisterUserUseCase,AuthenticateUserUseCase}.java`, `application/port/out/{UserRepository,PasswordHasher,TokenIssuer}.java`, services, `adapter/out/persistence/user/*`, `adapter/out/security/{BCryptPasswordHasher,JwtTokenIssuer}.java`, `adapter/in/web/AuthController.java`, `config/SecurityConfig.java`, `config/JwtProperties.java`; dependencies `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server` (for `NimbusJwtDecoder`/`JwtEncoder`), `spring-security-test`.

**Details:**
- `POST /api/v1/auth/register` `{username, password}` → 201; username 3–50 chars `[a-z0-9_.-]`, password ≥ 8 chars; duplicate → 409.
- `POST /api/v1/auth/login` → 200 `{token, expiresAt, username}`; wrong credentials → 401 problem detail (same message for unknown user and wrong password).
- `GET /api/v1/auth/me` → current user (protected).
- HS256 secret from `JWT_SECRET` env (default only for local dev, ≥ 32 bytes), TTL 1 h.
- Public: `GET /api/v1/pokemon/**`, `/api/v1/auth/register`, `/api/v1/auth/login`, `/actuator/health`, Swagger UI paths. Everything under `/api/v1/local-pokemon/**` requires a valid token.
- 401 and 403 responses must be `application/problem+json` (custom `AuthenticationEntryPoint` / `AccessDeniedHandler`).
- CORS: allow `http://localhost:5173` and `http://localhost:3000` (dev and dockerised frontend).
- Stateless sessions, CSRF disabled for the API.
- Seed demo user `demo` / `demo12345` (BCrypt hash) in a new Flyway migration.
- Existing `@WebMvcTest` tests must keep passing: import the security config in slice tests or use `@WithMockUser` where needed.

**Acceptance:** service unit tests; controller tests for register/login/me; security tests proving public vs protected access (200 without token on `/api/v1/pokemon`, 401 on `/api/v1/local-pokemon`); `AuthFlowIT` register → login → call protected endpoint.

---

## T5 — Cucumber acceptance tests per user story — wave C

**Goal:** executable specification: `src/test/resources/features/us01_enumeration.feature` … `us04_local_modification.feature`.

**Owns:** `src/test/resources/features/*`, `src/test/java/.../acceptance/*` (runner, step definitions, `CucumberSpringConfiguration`), dependencies `io.cucumber:cucumber-java`, `cucumber-spring`, `cucumber-junit-platform-engine`, `org.junit.platform:junit-platform-suite`, `io.rest-assured:rest-assured`.

**Details:** `@SpringBootTest(webEnvironment = RANDOM_PORT)` + Testcontainers PostgreSQL; PokeAPI stubbed with WireMock (`org.wiremock:wiremock-standalone`) pointed to via `pokeapi.base-url`; requests via RestAssured. Runner class name ends in `IT` so it runs under `mvn verify`. Scenarios use the business language of the user stories (Given/When/Then), including the 400/404/409 paths of US04.

---

## T6 — Frontend scaffold — wave A

**Goal:** `frontend/` with Vite + React + TypeScript, ready for pages.

**Owns:** everything under `frontend/`.

**Details:**
- `npm create vite@latest frontend -- --template react-ts`; add `@tanstack/react-query`, `react-router-dom`; dev tools: ESLint, Prettier, Vitest + Testing Library + jsdom.
- `src/api/client.ts`: `fetch` wrapper with base URL from `import.meta.env.VITE_API_URL` (default `http://localhost:8080`), attaches the JWT, parses `application/problem+json` into a typed `ApiError {status, title, detail, errors[]}`.
- `src/api/types.ts`: types for every DTO in `docs/PLAN.md` §3.
- `src/auth/AuthContext.tsx`: token in memory + `sessionStorage`, `login/logout/register`, `useAuth()`; `ProtectedRoute` component.
- `src/hooks/usePokemonPage.ts` with TanStack Query (`placeholderData: keepPreviousData` for smooth pagination).
- Layout: header (logo, nav: Browse / My collection / Login), responsive grid, loading skeletons, error banner component.
- First page: `/` Browse — grid of cards (sprite, name, #id, category, weight, ability chips) + pagination controls; page in the URL (`?page=2`).
- Zero warnings in the browser console (React keys, StrictMode double-render safe).

**Acceptance:** `npm run build`, `npm run lint`, `npm test` pass; the Browse page works against the running backend.

---

## T7 — Frontend pages — wave B, after T6

**Owns:** `frontend/src/pages/*`, `frontend/src/components/*` (new files), routes.

**Pages:**
- `/pokemon/:idOrName` — detail: artwork, types, stat bars, description, evolution chain (clickable), button "Add to my collection" (calls sync; prompts login if anonymous).
- `/login`, `/register` — forms with client-side validation mirroring the backend rules and server error display.
- `/collection` (protected) — paginated table/cards of local Pokémon; delete with confirmation; link to edit.
- `/collection/:id/edit` (protected) — form for `localName`, `region`, `notes`, `tags` (chip input); sends `version`; shows 409 "edited elsewhere, reload" message.
- Mutations invalidate the related TanStack Query keys; optimistic delete is a plus.
- Mobile first: works at 375 px width; keyboard accessible; `aria-*` labels on icon buttons.

**Acceptance:** component tests for the edit form validation and the 409 path; no console warnings.

---

## T8 — Full Docker Compose — wave C

**Owns:** `frontend/Dockerfile`, `frontend/nginx.conf`, `docker-compose.yml`, `.env.example`.

**Details:** multi-stage Node build → nginx serving `dist/` with SPA fallback (`try_files $uri /index.html`) and `/api` proxied to `backend:8080` (so the browser uses same-origin calls, no CORS in Docker); compose services `db`, `backend`, `frontend` (port 3000) with health checks and `depends_on: condition: service_healthy`; backend healthcheck on `/actuator/health`. Verify `docker compose up --build` from a clean clone, then open http://localhost:3000 and log in as `demo`.

---

## T9 — GenAI section — wave A (independent)

**Goal:** the mandatory GenAI part of the exercise, in `genai/`.

**Owns:** everything under `genai/`.

**Contents:**
- `genai/README.md`: the scenario (task-management REST API: CRUD tasks with `title, description, status, due_date`, tasks belong to a user), the tool used, and the sections below.
- `genai/prompt.md`: the exact prompt — role, stack, constraints (layering, validation rules, status enum, due date not in the past on create, ownership checks so users only see their own tasks, pagination, error format, tests required), output format request.
- `genai/output/`: the generated code as received (a representative sample: entity, DTOs, controller, service, tests) — **unedited**, so reviewers can compare.
- `genai/review.md`: how the output was validated (compile, run tests, read every line, check against requirements), a table of issues found (e.g. missing ownership check → IDOR, `due_date` timezone handling, no pagination cap, entity exposed in responses, status free text instead of enum, missing 404 vs 403 distinction), the correction for each, and how edge cases, authentication and validation were handled.
- `genai/corrected/`: the corrected version of the files that changed.

The reviewer cares about critical thinking: show concrete flaws found in AI output and why they matter.

---

## T10 — OpenAPI docs + demo seed data — wave B

**Owns:** `springdoc-openapi-starter-webmvc-ui` dependency (version compatible with Spring Boot 3.5), `config/OpenApiConfig.java` (title, version, JWT bearer security scheme), annotations on controllers (`@Operation`, `@ApiResponse` for 400/401/404/409), seed runner or migration that pre-syncs Pokémon #1–#12 into `local_pokemon` for the demo user experience.

**Details:** seeding must not call PokeAPI during tests (guard with a property `app.seed.enabled`, default `true`, `false` in tests) and must be idempotent. Swagger UI at `/swagger-ui.html` is public.

---

## T11 — README and presentation — wave C, last

**Owns:** `README.md`, `docs/PRESENTATION.md`, `docs/architecture.md` (Mermaid diagrams).

**README:** overview, features per user story, tech stack, architecture diagram, how to run (Docker one-liner, local dev for backend and frontend), demo credentials, API reference table, testing (how to run, what each level covers, coverage), caching, error format, design decisions and trade-offs, what I would do next, link to `genai/` and `docs/ai-log/`.

**PRESENTATION.md** (the "thought process" document the recruiter asked for): problem understanding, user stories → endpoints, architecture decisions and alternatives considered, TDD walkthrough with commit references, how AI tools were used and checked (summary of `docs/ai-log/prompts.md`), demo script (5 minutes), known limitations.
