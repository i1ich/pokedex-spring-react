package com.ilia.pokedex;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole auth story over real HTTP against PostgreSQL: register, log in, use the token on a protected
 * endpoint, and the negative paths. Also logs in as the user seeded by {@code V2__seed_demo_user.sql}, which
 * proves the seeded BCrypt hash really is the hash of {@code demo12345}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    private RestClient http;

    @BeforeEach
    void client() {
        // never throw on 4xx/5xx: the tests assert on the status themselves
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    @Test
    void registersLogsInAndCallsAProtectedEndpoint() {
        String username = "flow-" + UUID.randomUUID().toString().substring(0, 8);

        ResponseEntity<JsonNode> registered = post("/api/v1/auth/register", username, "a-good-password");
        assertThat(registered.getStatusCode().value()).isEqualTo(201);
        assertThat(registered.getBody().get("username").asText()).isEqualTo(username);
        assertThat(registered.getBody().get("role").asText()).isEqualTo("USER");

        Instant before = Instant.now();
        ResponseEntity<JsonNode> login = post("/api/v1/auth/login", username, "a-good-password");
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        String token = login.getBody().get("token").asText();
        assertThat(token).isNotBlank();
        assertThat(login.getBody().get("username").asText()).isEqualTo(username);
        assertThat(Instant.parse(login.getBody().get("expiresAt").asText()))
                .isBetween(before.plus(Duration.ofMinutes(59)), Instant.now().plus(Duration.ofMinutes(61)));

        ResponseEntity<JsonNode> protectedCall = get("/api/v1/local-pokemon", token);
        assertThat(protectedCall.getStatusCode().value()).isEqualTo(200);
        assertThat(protectedCall.getBody().has("items")).isTrue();

        ResponseEntity<JsonNode> me = get("/api/v1/auth/me", token);
        assertThat(me.getStatusCode().value()).isEqualTo(200);
        assertThat(me.getBody().get("username").asText()).isEqualTo(username);
        assertThat(me.getBody().get("role").asText()).isEqualTo("USER");
    }

    @Test
    void protectedEndpointsRejectRequestsWithoutAValidToken() {
        ResponseEntity<JsonNode> anonymous = get("/api/v1/local-pokemon", null);
        assertThat(anonymous.getStatusCode().value()).isEqualTo(401);
        assertThat(anonymous.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        assertThat(anonymous.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(anonymous.getBody().get("status").asInt()).isEqualTo(401);

        assertThat(get("/api/v1/local-pokemon", "garbage").getStatusCode().value()).isEqualTo(401);
        assertThat(get("/api/v1/auth/me", null).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void aTamperedTokenIsRejected() {
        String username = "tamper-" + UUID.randomUUID().toString().substring(0, 8);
        post("/api/v1/auth/register", username, "a-good-password");
        String token = post("/api/v1/auth/login", username, "a-good-password").getBody().get("token").asText();
        String[] parts = token.split("\\.");
        // flip the payload so the signature no longer matches
        String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "AA." + parts[2];

        assertThat(get("/api/v1/local-pokemon", tampered).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void theSeededDemoUserCanLogIn() {
        ResponseEntity<JsonNode> login = post("/api/v1/auth/login", "demo", "demo12345");

        assertThat(login.getStatusCode().value()).isEqualTo(200);
        assertThat(login.getBody().get("username").asText()).isEqualTo("demo");
        assertThat(get("/api/v1/local-pokemon", login.getBody().get("token").asText()).getStatusCode().value())
                .isEqualTo(200);
    }

    @Test
    void usernamesAreCaseInsensitiveAndNormalised() {
        assertThat(post("/api/v1/auth/login", "  DEMO ", "demo12345").getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void registeringTheSameUsernameTwiceIsAConflict() {
        String username = "dup-" + UUID.randomUUID().toString().substring(0, 8);
        assertThat(post("/api/v1/auth/register", username, "a-good-password").getStatusCode().value()).isEqualTo(201);

        ResponseEntity<JsonNode> second = post("/api/v1/auth/register", username.toUpperCase(), "another-password");

        assertThat(second.getStatusCode().value()).isEqualTo(409);
        assertThat(second.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
    }

    @Test
    void registrationRulesAreEnforced() {
        ResponseEntity<JsonNode> badName = post("/api/v1/auth/register", "no spaces", "a-good-password");
        assertThat(badName.getStatusCode().value()).isEqualTo(400);
        assertThat(badName.getBody().get("errors").get(0).get("field").asText()).isEqualTo("username");

        ResponseEntity<JsonNode> badPassword = post("/api/v1/auth/register", "someone", "short");
        assertThat(badPassword.getStatusCode().value()).isEqualTo(400);
        assertThat(badPassword.getBody().get("errors").get(0).get("field").asText()).isEqualTo("password");
    }

    @Test
    void unknownUserAndWrongPasswordGetTheSame401() {
        ResponseEntity<JsonNode> unknown = post("/api/v1/auth/login", "nobody-here", "whatever123");
        ResponseEntity<JsonNode> wrongPassword = post("/api/v1/auth/login", "demo", "not-the-password");

        assertThat(unknown.getStatusCode().value()).isEqualTo(401);
        assertThat(wrongPassword.getStatusCode().value()).isEqualTo(401);
        assertThat(unknown.getBody().get("detail").asText())
                .isEqualTo(wrongPassword.getBody().get("detail").asText())
                .isEqualTo("Invalid username or password");
    }

    @Test
    void healthIsPublicButOtherActuatorEndpointsAreNot() {
        assertThat(get("/actuator/health", null).getStatusCode().value()).isEqualTo(200);
        assertThat(get("/actuator/metrics", null).getStatusCode().value()).isEqualTo(401);
    }

    private ResponseEntity<JsonNode> post(String path, String username, String password) {
        return http.post().uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", password))
                .retrieve()
                .toEntity(JsonNode.class);
    }

    private ResponseEntity<JsonNode> get(String path, String token) {
        RestClient.RequestHeadersSpec<?> request = http.get().uri(path);
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request.retrieve().toEntity(JsonNode.class);
    }
}
