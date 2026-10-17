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
