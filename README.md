# pokedex-spring-react

Full-stack Pokédex: Spring Boot REST API (Clean Architecture, TDD, JWT auth) integrating PokeAPI with local PostgreSQL sync, caching, and a responsive React frontend. Dockerized.

> Work in progress. The final README (setup, architecture, API reference, demo credentials, presentation) is task T11 in [`docs/TASKS.md`](docs/TASKS.md).

## Quick start (current state)

```bash
docker compose up --build          # PostgreSQL + API on http://localhost:8080
curl "http://localhost:8080/api/v1/pokemon?page=0&size=5"
```

## Tests

```bash
cd backend
mvn test      # unit, web-slice, HTTP-adapter, caching and architecture tests (no Docker needed)
mvn verify    # + integration tests on PostgreSQL via Testcontainers (Docker required)
```

## Docs

- [`docs/PLAN.md`](docs/PLAN.md) — stack, architecture, endpoints, data model, test strategy, timeline
- [`docs/TASKS.md`](docs/TASKS.md) — task breakdown for parallel implementation
- [`docs/ai-log/prompts.md`](docs/ai-log/prompts.md) — prompts used and how the AI output was reviewed
