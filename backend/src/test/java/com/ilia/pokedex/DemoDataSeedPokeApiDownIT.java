package com.ilia.pokedex;

import com.ilia.pokedex.adapter.in.seed.DemoDataSeeder;
import com.ilia.pokedex.adapter.in.seed.DemoDataSeeder.SeedReport;
import com.ilia.pokedex.application.port.out.LocalPokemonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seeding on, PokeAPI unreachable (nothing listens on port 1): the application must still start and report
 * healthy, and every Pokémon of the pass is reported as failed rather than thrown.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.seed.enabled=true",
        "app.seed.pokemon-ids=1,2",
        "pokeapi.base-url=http://127.0.0.1:1",
        "pokeapi.connect-timeout=500ms"})
@Testcontainers
class DemoDataSeedPokeApiDownIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private LocalPokemonRepository repository;

    @Test
    void startsHealthyAndReportsEveryPokemonAsFailed() {
        ResponseEntity<String> health = RestClient.create("http://localhost:" + port).get()
                .uri("/actuator/health").retrieve().toEntity(String.class);
        assertThat(health.getStatusCode().value()).isEqualTo(200);

        SeedReport report = seeder.seed();

        assertThat(report.failed()).containsExactly(1, 2);
        assertThat(report.synced()).isEmpty();
        assertThat(repository.findById(1)).isEmpty();
    }
}
