# Architecture diagrams

Mermaid diagrams (rendered by GitHub). Names are taken from the code under `backend/src/main/java/com/ilia/pokedex`; the written rationale is in the [README](../README.md) and [`PRESENTATION.md`](PRESENTATION.md), the original design in [`PLAN.md`](PLAN.md).

1. [Layers and dependency direction](#1-layers-and-dependency-direction)
2. [Deployment with Docker Compose](#2-deployment-with-docker-compose)
3. [US03 sync: PokeAPI to the local store](#3-us03-sync-pokeapi-to-the-local-store)
4. [US04 update with optimistic locking](#4-us04-update-with-optimistic-locking)
5. [Data model](#5-data-model)

## 1. Layers and dependency direction

Single Maven module, Clean Architecture (ports and adapters). Solid arrows are compile-time dependencies and all of them point towards `domain`; dashed arrows mean "implements". `config` is the only place that knows every class: it builds the services (`UseCaseConfig`) and binds them to adapters.

```mermaid
flowchart LR
    subgraph IN["adapter.in  (driving adapters)"]
        WEB["adapter.in.web<br/>PokemonController, LocalPokemonController,<br/>AuthController, DTOs,<br/>GlobalExceptionHandler (RFC 7807)"]
        SEED["adapter.in.seed<br/>DemoDataSeeder"]
    end

    subgraph APP["application  (pure Java)"]
        PIN["application.port.in<br/>ListPokemonUseCase, GetPokemonDetailsUseCase,<br/>SyncPokemonUseCase, ListLocalPokemonUseCase,<br/>GetLocalPokemonUseCase, UpdateLocalPokemonUseCase,<br/>DeleteLocalPokemonUseCase, RegisterUserUseCase,<br/>AuthenticateUserUseCase"]
        SVC["application.service<br/>*Service classes<br/>(rules and validation)"]
        POUT["application.port.out<br/>PokemonCatalog, LocalPokemonRepository,<br/>UserRepository, PasswordHasher, TokenIssuer"]
    end

    DOM["domain  (pure Java)<br/>LocalPokemon, PokemonDetails, PokemonSnapshot,<br/>PageQuery, PokemonIdentifier, User, ...<br/>InvalidRequestException, ResourceNotFoundException,<br/>ConflictException, ExternalServiceException, ..."]

    subgraph OUT["adapter.out  (driven adapters)"]
        POKE["adapter.out.pokeapi<br/>PokeApiClient (HTTP + @Cacheable),<br/>PokeApiCatalogAdapter"]
        PERS["adapter.out.persistence<br/>JpaLocalPokemonRepositoryAdapter,<br/>JpaUserRepositoryAdapter, JPA entities"]
        SEC["adapter.out.security<br/>BCryptPasswordHasher, JwtTokenIssuer"]
    end

    CFG["config<br/>UseCaseConfig, SecurityConfig, PokeApiConfig,<br/>SeedConfig, OpenApiConfig, ClockConfig, ..."]

    WEB --> PIN
    SEED --> PIN
    SVC -.implements.-> PIN
    SVC --> POUT
    POKE -.implements.-> POUT
    PERS -.implements.-> POUT
    SEC -.implements.-> POUT
    WEB --> DOM
    SEED --> DOM
    SVC --> DOM
    PIN --> DOM
    POUT --> DOM
    POKE --> DOM
    PERS --> DOM
    CFG ==>|"creates services, wires adapters"| SVC
    CFG ==> OUT
    CFG ==> IN
```

What `ArchitectureTest` (ArchUnit) fails the build on:

| Rule | Meaning |
|---|---|
| `layers` | `Config` is accessed by nobody; `Adapters` only by `Config`; `Application` only by `Adapters` and `Config`; `Domain` only by `Application`, `Adapters`, `Config` |
| `coreIsFrameworkFree` | `domain` and `application` may not depend on `org.springframework..`, `jakarta.persistence..`, `jakarta.servlet..` or `com.fasterxml.jackson..` |
| `inboundAdaptersDoNotTouchOutboundAdapters` | nothing in `adapter.in` depends on `adapter.out` (a controller cannot call the PokeAPI client or a JPA repository) |
| `webUsesUseCasesNotServices` | nothing in `adapter.in` depends on `application.service`, only on the `port.in` interfaces |

## 2. Deployment with Docker Compose

`docker compose up --build` starts three containers. The browser talks only to nginx on port 3000: it serves the React build and proxies `/api/` to the backend, so the browser sees one origin (no CORS in Docker). Swagger UI and the actuator are served by the backend directly on port 8080 and are deliberately not proxied.

```mermaid
flowchart LR
    B["Browser"]

    subgraph COMPOSE["docker compose"]
        FE["frontend<br/>nginx-unprivileged<br/>host :3000 -> container :8080<br/>React build + SPA fallback"]
        BE["backend<br/>Spring Boot 3.5, Java 21<br/>host :8080 -> container :8080<br/>Caffeine cache in memory"]
        DB[("db<br/>PostgreSQL 16<br/>host :5432<br/>volume pgdata")]
    end

    P["PokeAPI<br/>https://pokeapi.co/api/v2"]

    B -->|"http://localhost:3000<br/>pages, assets"| FE
    FE -->|"/api/* proxy_pass http://backend:8080"| BE
    B -.->|"http://localhost:8080<br/>/swagger-ui.html, /actuator/health"| BE
    BE -->|"JDBC, Flyway migrations on start"| DB
    BE -->|"HTTPS, cached 6 h"| P

    FE -. "depends_on: service_healthy" .-> BE
    BE -. "depends_on: service_healthy" .-> DB
```

Health checks: `db` runs `pg_isready`, `backend` calls `/actuator/health`, `frontend` fetches `/`. Settings come from `.env` (see `.env.example`).

## 3. US03 sync: PokeAPI to the local store

`POST /api/v1/local-pokemon/sync` with `{"idOrName": "pikachu"}`. The security filter chain runs first (a missing, expired or forged token is a 401 `problem+json` and the controller is never reached). The service depends only on the two outbound ports; the cache sits in `PokeApiClient`, below the mapping adapter.

```mermaid
sequenceDiagram
    autonumber
    actor C as Client (SPA)
    participant F as Security filter chain
    participant LC as LocalPokemonController
    participant S as SyncPokemonService
    participant CAT as PokeApiCatalogAdapter<br/>(PokemonCatalog)
    participant PC as PokeApiClient<br/>(@Cacheable, Caffeine)
    participant API as PokeAPI
    participant R as JpaLocalPokemonRepositoryAdapter<br/>(LocalPokemonRepository)
    participant DB as PostgreSQL

    C->>F: POST /api/v1/local-pokemon/sync + Bearer JWT
    F->>LC: valid token
    LC->>S: sync("pikachu")
    S->>S: PokemonIdentifier.requireValid (400 if malformed)
    S->>CAT: findByIdOrName("pikachu")
    CAT->>PC: fetchPokemon("pikachu")
    alt cache hit (TTL 6 h)
        PC-->>CAT: cached PokemonResponse
    else cache miss
        PC->>API: GET /pokemon/pikachu
        API-->>PC: 200 JSON (404 becomes ResourceNotFoundException, 5xx or timeout becomes ExternalServiceException)
        PC-->>CAT: PokemonResponse (now cached)
    end
    CAT->>PC: fetchSpecies(id from species URL) for the English genus
    PC-->>CAT: SpeciesResponse (cache or PokeAPI)
    CAT-->>S: PokemonSnapshot (id, name, category, weight, height, sprite, artwork, abilities)
    S->>R: findById(25)
    R->>DB: SELECT local_pokemon
    DB-->>R: row or nothing
    R-->>S: Optional<LocalPokemon>
    alt not stored yet
        S->>S: LocalPokemon.firstSync(snapshot, now)
    else already stored
        S->>S: stored.resyncedWith(snapshot, now)<br/>(localName, region, notes, tags are kept)
    end
    S->>R: save(localPokemon)
    R->>DB: INSERT or UPDATE (saveAndFlush, @Version)
    alt insert lost a race on the primary key
        R-->>S: ConflictException (409, "synced concurrently, retry")
    else saved
        R-->>S: LocalPokemon (with version)
    end
    S-->>LC: SyncResult(pokemon, created)
    alt created
        LC-->>C: 201 Created + Location /api/v1/local-pokemon/25
    else refreshed
        LC-->>C: 200 OK
    end
```

Errors on the way out are mapped once, by `GlobalExceptionHandler`: `InvalidRequestException` 400, `ResourceNotFoundException` 404, `ConflictException` 409, `ExternalServiceException` 502, anything else 500 with a generic message.

## 4. US04 update with optimistic locking

`PUT /api/v1/local-pokemon/{id}` carries the `version` the client last read. A stale version is rejected in two places: the service compares it before writing (cheap, descriptive message), and the adapter translates JPA's `OptimisticLockingFailureException` for the race between the check and the write. Both end as the same domain `ConflictException`, so a conflict is never a 500.

```mermaid
sequenceDiagram
    autonumber
    actor C as Client (SPA)
    participant LC as LocalPokemonController
    participant S as UpdateLocalPokemonService
    participant R as JpaLocalPokemonRepositoryAdapter
    participant DB as PostgreSQL
    participant H as GlobalExceptionHandler

    C->>LC: PUT /api/v1/local-pokemon/25<br/>{localName, region, notes, tags, version: 3}
    LC->>LC: @Valid UpdateLocalPokemonRequest<br/>(shape, sizes, unknown or read-only property)
    opt body invalid
        LC-->>H: 400 with errors[{field, message}]
    end
    LC->>S: update(command)
    S->>S: id >= 1, version present, trim, tags lowercase kebab,<br/>at most 10 distinct tags (InvalidRequestException, 400)
    S->>R: findById(25)
    R->>DB: SELECT
    DB-->>R: row (version 4) or nothing
    alt record missing
        S-->>H: ResourceNotFoundException (404)
    else command.version (3) differs from stored.version (4)
        S-->>H: ConflictException (409, "modified by someone else")
    else versions match
        S->>R: save(stored.withProprietaryFields(...))
        R->>DB: UPDATE ... WHERE id = 25 AND version = (the version just read), via saveAndFlush
        alt another request committed first
            DB-->>R: 0 rows, OptimisticLockingFailureException
            R-->>H: ConflictException (409, "modified concurrently")
        else committed
            DB-->>R: version 4
            R-->>S: LocalPokemon
            S-->>LC: LocalPokemon
            LC-->>C: 200 OK with the new version
        end
    end
    H-->>C: application/problem+json {type, title, status, detail, instance[, errors]}
```

The frontend turns a 409 into a banner ("edited elsewhere") with a Reload button that refetches the record and restarts the form from it; Save stays disabled until then.

## 5. Data model

Flyway `V1__init.sql` (schema) and `V2__seed_demo_user.sql` (demo account). `local_pokemon.id` is the PokeAPI id, so syncing the same Pokémon twice is an upsert. There is no owner column: the collection is shared by all users (see the limitations in the README). The ability and tag tables are element collections of `LocalPokemonEntity` and are removed with the row (`ON DELETE CASCADE`).

```mermaid
erDiagram
    app_user {
        bigserial id PK
        varchar50 username UK "unique, lowercase"
        varchar100 password_hash "BCrypt"
        varchar20 role "default USER"
        timestamptz created_at
    }

    local_pokemon {
        integer id PK "PokeAPI id"
        varchar100 name UK
        varchar100 category "English genus"
        integer weight_hg ">= 0"
        integer height_dm ">= 0"
        varchar500 sprite_url
        varchar500 artwork_url
        varchar100 local_name "proprietary"
        varchar100 region "proprietary"
        varchar1000 notes "proprietary"
        timestamptz synced_at
        timestamptz updated_at
        bigint version "optimistic lock"
    }

    local_pokemon_ability {
        integer pokemon_id PK, FK
        integer position PK "keeps PokeAPI order"
        varchar100 ability
    }

    local_pokemon_tag {
        integer pokemon_id PK, FK
        varchar50 tag PK
    }

    local_pokemon ||--o{ local_pokemon_ability : "has"
    local_pokemon ||--o{ local_pokemon_tag : "tagged with"
```

`app_user` and `local_pokemon` are unrelated tables on purpose: users authenticate, the collection is not owned.
