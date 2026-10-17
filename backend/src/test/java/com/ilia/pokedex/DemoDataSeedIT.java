package com.ilia.pokedex;

import com.ilia.pokedex.adapter.in.seed.DemoDataSeeder;
import com.ilia.pokedex.adapter.in.seed.DemoDataSeeder.SeedReport;
import com.ilia.pokedex.application.port.out.LocalPokemonRepository;
import com.ilia.pokedex.domain.model.LocalPokemon;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The seeder inside the real application: with seeding on and a fake PokeAPI (JDK HTTP server serving the
 * test fixtures; #1 is served, #2 always answers 500), the background run stores #1, logs #2 as failed and
 * leaves the application up. A second pass after a local edit must not call PokeAPI for #1 again nor touch
 * the edit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.seed.enabled=true",
        "app.seed.pokemon-ids=1,2"})
@Testcontainers
class DemoDataSeedIT {

    private static final Map<String, AtomicInteger> REQUESTS = new ConcurrentHashMap<>();
    private static final HttpServer POKEAPI = startFakePokeApi();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void pokeApi(DynamicPropertyRegistry registry) {
        registry.add("pokeapi.base-url", () -> "http://localhost:" + POKEAPI.getAddress().getPort());
    }

    @AfterAll
    static void stopFakePokeApi() {
        POKEAPI.stop(0);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private LocalPokemonRepository repository;

    @Test
    void seedsInTheBackgroundSurvivesAFailureAndNeverOverwritesLocalEdits() {
        // startup returned (we are running) and the background pass stores #1; #2 fails without effect
        await().atMost(Duration.ofSeconds(30)).until(() -> repository.findById(1).isPresent());
        await().atMost(Duration.ofSeconds(30)).until(() -> requests("/pokemon/2") >= 1);
        assertThat(repository.findById(1).orElseThrow().name()).isEqualTo("bulbasaur");
        assertThat(repository.findById(2)).isEmpty();
        assertThat(health().getStatusCode().value()).isEqualTo(200);
        assertThat(health().getBody()).contains("UP");

        // the user edits #1, then the application "restarts" (another seeding pass)
        LocalPokemon stored = repository.findById(1).orElseThrow();
        repository.save(stored.withProprietaryFields("Bulby", "Kanto", "my notes", List.of("starter"), Instant.now()));
        int pokemonCallsBefore = requests("/pokemon/1");

        SeedReport second = seeder.seed();

        assertThat(second.skipped()).containsExactly(1);
        assertThat(second.failed()).containsExactly(2);
        assertThat(second.synced()).isEmpty();
        assertThat(requests("/pokemon/1")).as("PokeAPI calls for #1").isEqualTo(pokemonCallsBefore);
        LocalPokemon after = repository.findById(1).orElseThrow();
        assertThat(after.localName()).isEqualTo("Bulby");
        assertThat(after.region()).isEqualTo("Kanto");
        assertThat(after.notes()).isEqualTo("my notes");
        assertThat(after.tags()).containsExactly("starter");
    }

    private ResponseEntity<String> health() {
        return RestClient.create("http://localhost:" + port).get().uri("/actuator/health")
                .retrieve().toEntity(String.class);
    }

    private static int requests(String path) {
        AtomicInteger count = REQUESTS.get(path);
        return count == null ? 0 : count.get();
    }

    private static HttpServer startFakePokeApi() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                REQUESTS.computeIfAbsent(path, key -> new AtomicInteger()).incrementAndGet();
                String fixture = switch (path) {
                    case "/pokemon/1" -> "pokemon-bulbasaur";
                    case "/pokemon-species/1" -> "species-1";
                    default -> null;
                };
                byte[] body = fixture == null ? new byte[0] : fixture(fixture);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(fixture == null ? 500 : 200, fixture == null ? -1 : body.length);
                if (fixture != null) {
                    exchange.getResponseBody().write(body);
                }
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream in = DemoDataSeedIT.class.getResourceAsStream("/pokeapi/" + name + ".json")) {
            return in.readAllBytes();
        }
    }
}
