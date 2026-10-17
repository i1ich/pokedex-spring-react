# Prompt used for the GenAI exercise

This is the exact prompt that was given, unchanged, to a fresh code-generating model that saw nothing else from this repository. The generated answer is stored unedited in `output/`.

```text
You are a senior Java backend engineer. Build a task-management REST API.

STACK
- Java 21, Spring Boot 3.5, Maven, Spring Web, Spring Data JPA, PostgreSQL, Bean Validation.
- Spring Security with JWT bearer authentication is already provided by the platform: do not implement login or token handling. Assume every /api/** request is authenticated and the authenticated username is available from the SecurityContext (Authentication.getName()). A user table with a unique username already exists; you may reference it by username.
- Tests: JUnit 5, Mockito, AssertJ, MockMvc (@WebMvcTest).

SCENARIO
Users manage tasks. A task has: id, title, description, status, due_date, and it belongs to exactly one user. Provide CRUD:
- POST   /api/tasks        create a task
- GET    /api/tasks        list the caller's tasks (paginated)
- GET    /api/tasks/{id}   get one task
- PUT    /api/tasks/{id}   replace a task
- DELETE /api/tasks/{id}   delete a task

CONSTRAINTS
1. Layering: controller -> service -> repository. Controllers talk only to the service. Use request/response DTOs (Java records) at the API edge; JPA entities must never be exposed in or accepted from the API. Map explicitly.
2. Validation (Bean Validation on the request DTOs):
   - title: required, not blank, 1-200 characters.
   - description: optional, at most 2000 characters.
   - status: enum TODO, IN_PROGRESS, DONE; defaults to TODO on create when omitted. Reject unknown values with 400.
   - due_date: optional ISO-8601 date (JSON name "due_date"); on create it must not be in the past.
3. Ownership: a user can only see, update and delete their own tasks. The owner is always taken from the security context, never from the request body or a query parameter. Listing returns only the caller's tasks.
4. Pagination: GET /api/tasks accepts page (default 0), size (default 20, maximum 100) and an optional status filter; return a page object with content, page, size, totalElements, totalPages. Sort by due date then id, deterministically.
5. Errors: use RFC 7807 application/problem+json for every error (type, title, status, detail, instance). A 400 caused by validation must include an "errors" array with {field, message} entries. Handle malformed JSON and invalid enum values as 400 as well. Use a @RestControllerAdvice.
6. Status codes: 201 with a Location header on create, 200 on read/update/list, 204 on delete, 400 validation, 401 unauthenticated, 404 when the task does not exist, and the appropriate code for any other failure you identify. Never leak stack traces.
7. Persistence: a Flyway migration (V1) creating the tasks table with sensible types, constraints and an index for the owner lookup; spring.jpa.hibernate.ddl-auto=validate.
8. Tests required: unit tests for the service (Mockito) covering success and failure paths including ownership and validation of the due date; controller tests with MockMvc covering status codes, validation errors, the problem+json body, pagination parameters and authentication.

OUTPUT FORMAT
- Complete, compilable files only (no placeholders, no "..." elisions), including pom.xml and application.yml.
- One fenced code block per file, each preceded by a line with its relative path (for example src/main/java/com/example/tasks/TaskController.java). Use the base package com.example.tasks.
- After the files, add a short list of the assumptions you made.
```
