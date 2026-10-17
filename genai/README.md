# GenAI section: a task-management API generated, reviewed and corrected

The mandatory GenAI part of the exercise. A code-generating model was asked for a small REST API; its output was kept unedited, then validated, reviewed critically and corrected test-first.

## Scenario

A task-management REST API (Java 21, Spring Boot 3.5, Spring Data JPA, PostgreSQL, Bean Validation). Users manage their own tasks: create, list (paginated), read, replace and delete a task with `title`, `description`, `status` (`TODO`, `IN_PROGRESS`, `DONE`) and `due_date`. Every task belongs to one user, taken from the authenticated principal (JWT bearer auth is assumed to be provided by the platform). Errors are RFC 7807 `application/problem+json`.

## Tool and process

| Phase | Who | What |
|---|---|---|
| 1. Prompt | A Claude Code subagent (Claude Sonnet 5.5) | Wrote [`prompt.md`](prompt.md): role, stack, layering, validation rules, ownership, pagination, error format, tests, output format. Written as a realistic good-faith prompt, not weakened to provoke mistakes |
| 2. Generation | A separate, fresh Claude Code subagent (Claude Sonnet 5.5), single pass | Saw only the text of `prompt.md` (no repository, no follow-up). Its full answer is `output/RESPONSE.md`; the files in `output/` are its code blocks split out unchanged |
| 3. Review and correction | A Claude Code subagent (Claude Sonnet 5.5), independent from phase 2 | Compiled, ran, read and probed the output; wrote [`review.md`](review.md); built `corrected/` with red/green commits |

Nothing under `output/` was edited. The coordinating session ran the phases; the human owner decides what is kept.

## Layout

```text
genai/
  prompt.md        the exact prompt
  output/          AI output as received (pom.xml, src/, RESPONSE.md); does not compile its own tests
  corrected/       complete Maven project: output + fixes + extra tests
  review.md        method, results, issue table, edge cases, authentication, validation, what the AI got right
```

## Headline results

- As delivered the test code does not compile (a package-private class used from another package). With a one-word fix its 43 tests pass.
- Ownership (no IDOR), DTO separation, problem+json and validation rules were mostly right on the first pass; details in `review.md`, section 8.
- Real flaws found and fixed: 500 on an unsatisfiable `Accept` header, enum ordinals accepted, `due_date` with a time part silently truncated, the past-date rule implemented twice with the host clock and zone, page-size cap only in the controller, and tests that never touched the database.
- Flaws documented and left open on purpose: lost updates on PUT, no 401 body check, 409 for a user missing from `users`, scalar coercion, silent unknown properties.

## Run the corrected project

Needs JDK 21. From the repository root (Windows: `backend\mvnw.cmd`):

```bash
# unit and web tests, no Docker needed (61 tests)
backend/mvnw -f genai/corrected/pom.xml test

# also the Testcontainers integration tests (11 ITs, needs Docker)
backend/mvnw -f genai/corrected/pom.xml verify
```

The ITs start `postgres:16-alpine`, run the Flyway migration and use a test-only `users` table (`src/test/resources/db/migration/V0_1__users_stub.sql`) in place of the platform's. The application itself is not runnable stand-alone without that table and the platform's JWT configuration; this project is a module under review, not a deployable service. Configuration additions in `corrected/`: `app.time-zone` (env `APP_TIME_ZONE`, default `UTC`) decides what "today" means for the due-date rule.

To compare with the original: `diff -r genai/output genai/corrected` (ignoring `RESPONSE.md`), or follow the commits `chore(genai): corrected/ baseline`, `[red]`, `[green]`.

See [`review.md`](review.md) for the full analysis.
