package com.ilia.pokedex;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The generated OpenAPI document and Swagger UI, fetched over real HTTP from the full application without a
 * token: both are public, and the document must describe the real API (paths, bearer scheme, which
 * operations are protected, the problem+json error responses).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OpenApiIT {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    private RestClient http;
    private JsonNode api;

    @BeforeEach
    void fetchDocument() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
        ResponseEntity<JsonNode> response = http.get().uri("/v3/api-docs").retrieve().toEntity(JsonNode.class);
        assertThat(response.getStatusCode().value()).as("GET /v3/api-docs without a token").isEqualTo(200);
        api = response.getBody();
    }

    @Test
    void describesTheApplication() {
        assertThat(api.at("/openapi").asText()).startsWith("3.");
        assertThat(api.at("/info/title").asText()).isEqualTo("Pokédex API");
        assertThat(api.at("/info/version").asText()).isNotBlank();
        assertThat(api.at("/info/description").asText()).isNotBlank();
    }

    @Test
    void listsEveryPath() {
        List<String> paths = new ArrayList<>();
        api.get("paths").fieldNames().forEachRemaining(paths::add);

        assertThat(paths).contains(
                "/api/v1/pokemon",
                "/api/v1/pokemon/{idOrName}",
                "/api/v1/local-pokemon/sync",
                "/api/v1/local-pokemon",
                "/api/v1/local-pokemon/{id}",
                "/api/v1/auth/register",
                "/api/v1/auth/login",
                "/api/v1/auth/me");
    }

    @Test
    void declaresTheBearerJwtScheme() {
        JsonNode scheme = api.at("/components/securitySchemes/bearerAuth");

        assertThat(scheme.isMissingNode()).isFalse();
        assertThat(scheme.get("type").asText()).isEqualTo("http");
        assertThat(scheme.get("scheme").asText()).isEqualTo("bearer");
        assertThat(scheme.get("bearerFormat").asText()).isEqualTo("JWT");
    }

    @ParameterizedTest(name = "{0} {1} requires the bearer token")
    @CsvSource({
            "post, /api/v1/local-pokemon/sync",
            "get, /api/v1/local-pokemon",
            "get, /api/v1/local-pokemon/{id}",
            "put, /api/v1/local-pokemon/{id}",
            "delete, /api/v1/local-pokemon/{id}",
            "get, /api/v1/auth/me"})
    void protectedOperationsDeclareBearerAuth(String method, String path) {
        JsonNode security = operation(method, path).path("security");

        assertThat(security.isArray()).as("security of %s %s", method, path).isTrue();
        assertThat(security.toString()).contains("bearerAuth");
    }

    @ParameterizedTest(name = "{0} {1} is public")
    @CsvSource({
            "get, /api/v1/pokemon",
            "get, /api/v1/pokemon/{idOrName}",
            "post, /api/v1/auth/register",
            "post, /api/v1/auth/login"})
    void publicOperationsDeclareNoSecurity(String method, String path) {
        assertThat(operation(method, path).has("security")).as("security of %s %s", method, path).isFalse();
    }

    @ParameterizedTest(name = "{0} {1} documents {2} as problem+json")
    @CsvSource({
            "get, /api/v1/pokemon, 400",
            "get, /api/v1/pokemon, 502",
            "get, /api/v1/pokemon/{idOrName}, 404",
            "get, /api/v1/pokemon/{idOrName}, 502",
            "post, /api/v1/local-pokemon/sync, 400",
            "post, /api/v1/local-pokemon/sync, 401",
            "post, /api/v1/local-pokemon/sync, 404",
            "post, /api/v1/local-pokemon/sync, 502",
            "get, /api/v1/local-pokemon, 401",
            "get, /api/v1/local-pokemon/{id}, 401",
            "get, /api/v1/local-pokemon/{id}, 404",
            "put, /api/v1/local-pokemon/{id}, 400",
            "put, /api/v1/local-pokemon/{id}, 401",
            "put, /api/v1/local-pokemon/{id}, 404",
            "put, /api/v1/local-pokemon/{id}, 409",
            "delete, /api/v1/local-pokemon/{id}, 401",
            "delete, /api/v1/local-pokemon/{id}, 404",
            "post, /api/v1/auth/register, 400",
            "post, /api/v1/auth/register, 409",
            "post, /api/v1/auth/login, 401",
            "get, /api/v1/auth/me, 401"})
    void errorResponsesAreProblemJson(String method, String path, String status) {
        JsonNode content = operation(method, path).at("/responses/" + status + "/content");

        assertThat(content.isMissingNode()).as("%s %s documents %s", method, path, status).isFalse();
        assertThat(content.has(PROBLEM_JSON)).as("media type of %s", status).isTrue();
        assertThat(content.at("/" + PROBLEM_JSON.replace("/", "~1") + "/schema").toString())
                .contains("ProblemDetail");
    }

    @Test
    void successResponsesAreDocumented() {
        assertThat(operation("post", "/api/v1/local-pokemon/sync").at("/responses/201").isMissingNode()).isFalse();
        assertThat(operation("post", "/api/v1/local-pokemon/sync").at("/responses/200").isMissingNode()).isFalse();
        assertThat(operation("delete", "/api/v1/local-pokemon/{id}").at("/responses/204").isMissingNode())
                .isFalse();
        assertThat(operation("get", "/api/v1/pokemon").at("/responses/200").isMissingNode()).isFalse();
    }

    @Test
    void everySuccessResponseDescribesItsBody() {
        api.get("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(op -> {
            if (op.getKey().equals("delete")) {
                return;
            }
            JsonNode responses = op.getValue().get("responses");
            boolean described = false;
            for (String status : List.of("200", "201")) {
                described |= responses.at("/" + status + "/content").toString().contains("schema");
            }
            assertThat(described).as("success body of %s %s", op.getKey(), path.getKey()).isTrue();
        }));
    }

    @Test
    void everyOperationHasASummaryAndATag() {
        api.get("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(op -> {
            String where = op.getKey() + " " + path.getKey();
            assertThat(op.getValue().path("summary").asText()).as("summary of %s", where).isNotBlank();
            assertThat(op.getValue().path("tags")).as("tags of %s", where).isNotEmpty();
        }));
    }

    @Test
    void defineTheProblemDetailSchema() {
        JsonNode problem = api.at("/components/schemas/ProblemDetail");

        assertThat(problem.isMissingNode()).isFalse();
        assertThat(problem.at("/properties/status").isMissingNode()).isFalse();
        assertThat(problem.at("/properties/detail").isMissingNode()).isFalse();
    }

    @Test
    void swaggerUiIsPublic() {
        ResponseEntity<String> entry = http.get().uri("/swagger-ui.html").retrieve().toEntity(String.class);
        assertThat(entry.getStatusCode().value()).as("/swagger-ui.html").isIn(200, 302);
        if (entry.getStatusCode().is3xxRedirection()) {
            assertThat(entry.getHeaders().getLocation().getPath()).startsWith("/swagger-ui/");
        }

        ResponseEntity<String> page = http.get().uri("/swagger-ui/index.html").retrieve().toEntity(String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
        assertThat(page.getHeaders().getContentType().toString()).startsWith("text/html");
        assertThat(page.getBody()).containsIgnoringCase("swagger");
    }

    private JsonNode operation(String method, String path) {
        JsonNode operation = api.get("paths").path(path).path(method);
        assertThat(operation.isMissingNode()).as("operation %s %s", method, path).isFalse();
        return operation;
    }
}
