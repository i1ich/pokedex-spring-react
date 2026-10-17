package com.ilia.pokedex;

import com.fasterxml.jackson.databind.JsonNode;
import com.ilia.pokedex.application.port.out.LocalPokemonRepository;
import com.ilia.pokedex.domain.model.LocalPokemon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US04 over real HTTP against PostgreSQL: the optimistic-locking story (update with the current version,
 * then replay with the old one), the problem+json shape of each failure, and delete. Records are seeded
 * through the repository port, so PokeAPI is never called.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UpdateLocalPokemonIT {

    private static final String BASE = "/api/v1/local-pokemon/";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private LocalPokemonRepository repository;

    private RestClient http;
    private String token;

    @BeforeEach
    void loginAndClean() {
        // never throw on 4xx/5xx: the tests assert on the status themselves
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
        token = http.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", "demo", "password", "demo12345"))
                .retrieve().body(JsonNode.class).get("token").asText();
        repository.deleteById(25);
        repository.deleteById(26);
    }

    @Test
    void updatesWithTheCurrentVersionThenRejectsTheOldOneWith409() {
        LocalPokemon seeded = seed(25, "pikachu");
        long version = seeded.version();

        ResponseEntity<JsonNode> updated = put(25, Map.of(
                "localName", "  Sparky ", "region", "Kanto", "notes", "my notes",
                "tags", List.of("Electric", "starter", "electric"), "version", version));

        assertThat(updated.getStatusCode().value()).isEqualTo(200);
        JsonNode body = updated.getBody();
        assertThat(body.get("version").asLong()).isEqualTo(version + 1);
        assertThat(body.get("localName").asText()).isEqualTo("Sparky");
        assertThat(body.get("region").asText()).isEqualTo("Kanto");
        assertThat(body.get("notes").asText()).isEqualTo("my notes");
        assertThat(body.get("tags")).extracting(JsonNode::asText).containsExactly("electric", "starter");
        assertThat(body.get("name").asText()).isEqualTo("pikachu");
        assertThat(body.get("abilities")).extracting(JsonNode::asText).containsExactly("static", "lightning-rod");
        assertThat(Instant.parse(body.get("updatedAt").asText())).isAfter(Instant.parse(body.get("syncedAt").asText()));
        assertThat(get(25).getBody().get("localName").asText()).isEqualTo("Sparky");

        // a second client still holding the old version
        ResponseEntity<JsonNode> stale = put(25, Map.of("localName", "Stale", "version", version));

        assertThat(stale.getStatusCode().value()).isEqualTo(409);
        assertThat(stale.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        assertThat(stale.getBody().get("status").asInt()).isEqualTo(409);
        assertThat(stale.getBody().get("title").asText()).isEqualTo("Conflict");
        assertThat(stale.getBody().get("detail").asText()).contains("reload and try again");
        // nothing was overwritten
        JsonNode current = get(25).getBody();
        assertThat(current.get("localName").asText()).isEqualTo("Sparky");
        assertThat(current.get("version").asLong()).isEqualTo(version + 1);
    }

    @Test
    void aSecondUpdateWithTheNewVersionSucceeds() {
        long version = seed(25, "pikachu").version();
        long next = put(25, Map.of("localName", "One", "version", version)).getBody().get("version").asLong();

        ResponseEntity<JsonNode> second = put(25, Map.of("localName", "Two", "version", next));

        assertThat(second.getStatusCode().value()).isEqualTo(200);
        assertThat(second.getBody().get("version").asLong()).isEqualTo(next + 1);
    }

    @Test
    void omittedFieldsAreClearedBecausePutReplacesTheProprietaryFields() {
        long version = seed(25, "pikachu").version();
        long next = put(25, Map.of("localName", "Sparky", "tags", List.of("a"), "version", version))
                .getBody().get("version").asLong();

        ResponseEntity<JsonNode> cleared = put(25, Map.of("version", next));

        assertThat(cleared.getStatusCode().value()).isEqualTo(200);
        assertThat(cleared.getBody().get("localName").isNull()).isTrue();
        assertThat(cleared.getBody().get("tags")).isEmpty();
    }

    @Test
    void rejectsInvalidUpdatesWith400ProblemDetailsThatNameTheField() {
        long version = seed(25, "pikachu").version();

        assertBadRequest(put(25, Map.of("localName", "   ", "version", version)), "localName");
        assertBadRequest(put(25, Map.of("tags", List.of("Not Kebab"), "version", version)), "tags[0]");
        assertBadRequest(put(25, Map.of("name", "raichu", "version", version)), "name");
        assertBadRequest(put(25, Map.of("stats", List.of(), "version", version)), "stats");
        assertBadRequest(put(25, Map.of("localName", "x")), "version");
        assertBadRequest(putRaw(25, "{not json"), "request");
        assertBadRequest(putRaw(25, "{\"version\":\"abc\"}"), "version");
        assertBadRequest(put(0, Map.of("version", 0)), "id");
        assertBadRequest(putTo("abc", Map.of("version", 0)), "id");
        // the record was not changed by any of them
        JsonNode current = get(25).getBody();
        assertThat(current.get("version").asLong()).isEqualTo(version);
        assertThat(current.get("name").asText()).isEqualTo("pikachu");
    }

    @Test
    void answers404ForAnUnknownRecordOnPutAndDelete() {
        assertThat(put(26, Map.of("version", 0)).getStatusCode().value()).isEqualTo(404);

        ResponseEntity<JsonNode> deleted = delete(26, token);
        assertThat(deleted.getStatusCode().value()).isEqualTo(404);
        assertThat(deleted.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
    }

    @Test
    void deletesARecordAndThenAnswers404() {
        seed(26, "raichu");

        ResponseEntity<JsonNode> deleted = delete(26, token);

        assertThat(deleted.getStatusCode().value()).isEqualTo(204);
        assertThat(deleted.getBody()).isNull();
        assertThat(get(26).getStatusCode().value()).isEqualTo(404);
        assertThat(delete(26, token).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void updateAndDeleteRequireAToken() {
        long version = seed(25, "pikachu").version();

        ResponseEntity<JsonNode> put = http.put().uri(BASE + 25)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("version", version))
                .retrieve().toEntity(JsonNode.class);
        assertThat(put.getStatusCode().value()).isEqualTo(401);
        assertThat(put.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        assertThat(delete(25, null).getStatusCode().value()).isEqualTo(401);
        assertThat(repository.findById(25)).isPresent();
    }

    // --- helpers ----------------------------------------------------------------------------------------------

    private void assertBadRequest(ResponseEntity<JsonNode> response, String field) {
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        assertThat(response.getBody().get("errors")).extracting(e -> e.get("field").asText()).contains(field);
    }

    private LocalPokemon seed(int id, String name) {
        Instant now = Instant.now();
        return repository.save(new LocalPokemon(id, name, "Mouse Pokémon", 60, 4, "https://img/" + id + ".png",
                null, List.of("static", "lightning-rod"), null, null, null, List.of(), now, now, null));
    }

    private ResponseEntity<JsonNode> get(int id) {
        return http.get().uri(BASE + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().toEntity(JsonNode.class);
    }

    private ResponseEntity<JsonNode> put(int id, Map<String, ?> body) {
        return putTo(String.valueOf(id), body);
    }

    private ResponseEntity<JsonNode> putTo(String id, Map<String, ?> body) {
        return http.put().uri(BASE + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().toEntity(JsonNode.class);
    }

    private ResponseEntity<JsonNode> putRaw(int id, String json) {
        return http.put().uri(BASE + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).body(json)
                .retrieve().toEntity(JsonNode.class);
    }

    private ResponseEntity<JsonNode> delete(int id, String bearer) {
        RestClient.RequestHeadersSpec<?> request = http.delete().uri(BASE + id);
        if (bearer != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        }
        return request.retrieve().toEntity(JsonNode.class);
    }
}
