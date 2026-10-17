# Implementation Plan

Ballast Lane Java technical exercise. Deadline: **Monday 19 Oct 2026, 18:00 (UTC-3)**.
Target submission: Monday ~15:00, leaving a buffer for a final clean-machine `docker compose up` check.

## 1. Stack

| Area | Choice | Why |
|---|---|---|
| Language / runtime | Java 21 | LTS, records, virtual threads for parallel PokeAPI calls |
| Framework | Spring Boot 3.5 | Web (RestClient), Data JPA, Security, Validation, Cache |
| Database | PostgreSQL 16 + Flyway | Relational store for local replicas and users; versioned schema |
| Cache | Caffeine via Spring Cache | Bounded, TTL-based in-memory cache for PokeAPI responses |
| Auth | Spring Security + JWT (HS256), BCrypt | Stateless public/protected routes |
| Tests | JUnit 5, Mockito, AssertJ, MockMvc, MockRestServiceServer, Testcontainers, ArchUnit, Cucumber | Unit → slice → integration → BDD acceptance |
| Frontend | React + TypeScript + Vite, TanStack Query, React Router | Server state in TanStack Query, auth state in Context |
| Delivery | Dockerfiles (backend, frontend) + `docker-compose.yml` | One-command start with seeded data |

## 2. Architecture (Clean Architecture, single Maven module)

Dependencies point inward only. Enforced by `ArchitectureTest` (ArchUnit), so a violation fails the build.

```
adapter.in.web ──► application.port.in ◄── application.service ──► application.port.out ◄── adapter.out.*
                              │                       │                                            
                              └──────────► domain ◄───┘                                            
config  (Spring wiring: builds services from ports; the only place that knows everything)
```

| Package | Contents | May depend on |
|---|---|---|
| `domain` | Records and domain exceptions (`PokemonSummary`, `PageResult`, `InvalidRequestException`, …) | nothing (pure Java) |
| `application.port.in` | Use-case interfaces (`ListPokemonUseCase`, …) | `domain` |
| `application.port.out` | Interfaces the core needs (`PokemonCatalog`, `LocalPokemonRepository`, `UserRepository`, `PasswordHasher`, `TokenIssuer`) | `domain` |
| `application.service` | Use-case implementations: business rules and validation | `domain`, ports |
| `adapter.in.web` | Controllers, request/response DTOs, `GlobalExceptionHandler` (RFC 7807) | `application.port.in`, `domain` |
| `adapter.out.pokeapi` | `PokeApiClient` (HTTP + `@Cacheable`), `PokeApiCatalogAdapter` (mapping, parallel fan-out) | ports, `domain` |
| `adapter.out.persistence` | JPA entities, Spring Data repositories, port adapters | ports, `domain` |
| `adapter.out.security` | JWT and BCrypt implementations of security ports | ports |
| `config` | `@Configuration` classes, properties, security filter chain | everything |

`domain` and `application` contain **no Spring or JPA annotations**. Services are instantiated in `config/UseCaseConfig`.

## 3. User stories → endpoints

| Story | Endpoint | Auth |
|---|---|---|
| US01 Enumeration | `GET /api/v1/pokemon?page=0&size=20` → page of {id, name, spriteUrl, category, weightKg, abilities} | public |
| US02 Detailed view | `GET /api/v1/pokemon/{idOrName}` → image, stats, description, evolution chain | public |
| US03 Data sync (Create) | `POST /api/v1/local-pokemon/sync` `{ "idOrName": "pikachu" }` → 201 + Location | protected |
| Local read | `GET /api/v1/local-pokemon?page&size`, `GET /api/v1/local-pokemon/{id}` | protected |
| US04 Update | `PUT /api/v1/local-pokemon/{id}` `{localName, region, tags[], notes, version}` → 200 / 400 / 404 / 409 | protected |
| Delete | `DELETE /api/v1/local-pokemon/{id}` → 204 / 404 | protected |
| Auth | `POST /api/v1/auth/register`, `POST /api/v1/auth/login` → `{token, expiresAt, username}`; `GET /api/v1/auth/me` | public / protected |

Errors: `application/problem+json` with `type, title, status, detail, instance` and, for 400s, `errors: [{field, message}]`.
Pagination response: `{ items, page, size, totalElements, totalPages }`.

## 4. Data model (Flyway `V1__init.sql`)

- `app_user` (id PK, username unique, password_hash, role, created_at) — the "secondary collection for user management".
- `local_pokemon` (id PK = PokeAPI id, name, category, weight_hg, height_dm, sprite_url, artwork_url, **local_name, region, notes**, synced_at, updated_at, version) — primary entity; bold fields are proprietary.
- `local_pokemon_ability` (pokemon_id FK, ability), `local_pokemon_tag` (pokemon_id FK, tag) — collections.
- Seed (`V2__seed.sql` / startup runner): demo user `demo / demo12345`, first 12 Pokémon pre-synced.

## 5. Testing strategy

| Level | Tool | What |
|---|---|---|
| Unit (TDD driver) | JUnit 5 + Mockito + AssertJ | Every service: rules, validation, port interactions |
| Web slice | `@WebMvcTest` + MockMvc | Status codes, JSON shape, problem details, security rules |
| HTTP adapter | `MockRestServiceServer` | PokeAPI mapping, 404 → not found, 5xx → 502 |
| Persistence / integration | `@SpringBootTest` + Testcontainers PostgreSQL | Flyway migrations, repositories, end-to-end flows |
| Architecture | ArchUnit | Layer dependency rules |
| Acceptance | Cucumber | One feature file per user story (US01–US04) |
| Frontend | Vitest + Testing Library | Hooks and key components |

TDD evidence: commits follow `test(usXX): … [red]` → `feat(usXX): … [green]` → `refactor: …`.

## 6. Timeline

| When | Milestone |
|---|---|
| Sat AM | Plan, skeleton, US01 TDD cycle (this commit) |
| Sat PM | Wave A in parallel: US02, persistence + US03, auth, frontend scaffold, GenAI section |
| Sun | Wave B: US04 + local CRUD, frontend pages, OpenAPI; Wave C: Cucumber, Docker Compose, README, presentation |
| Mon AM | Full review, clean-clone `docker compose up`, browser console check, final README |
| Mon ~15:00 | Submit (one public repo link) |

## 7. Risks

- **PokeAPI latency / rate limits** → Caffeine cache + parallel fan-out on virtual threads + timeouts; 502 problem detail on upstream failure.
- **Forms vs species** (e.g. `deoxys-normal`) → species is read from the `species` link of the Pokémon resource, not by name.
- **Parallel agents editing shared files** (`pom.xml`, `UseCaseConfig`, `GlobalExceptionHandler`, `application.yml`) → see rules in `docs/TASKS.md`.
- **No submissions after the deadline** → submit only once, after the Monday checklist.
