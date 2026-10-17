package com.ilia.pokedex.adapter.out.persistence;

import com.ilia.pokedex.domain.model.LocalPokemon;
import com.ilia.pokedex.domain.model.PageQuery;
import com.ilia.pokedex.domain.model.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the adapter against a real PostgreSQL with the Flyway schema and {@code ddl-auto: validate}, so the
 * entity mappings are checked against {@code V1__init.sql}. Each adapter call is its own transaction (as in
 * production), which is why the class opts out of the default test-managed transaction.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaLocalPokemonRepositoryAdapter.class)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JpaLocalPokemonRepositoryAdapterIT {

    // PostgreSQL stores microseconds, so test timestamps are truncated to what survives the round trip.
    private static final Instant T0 = Instant.parse("2026-10-17T10:00:00Z").truncatedTo(ChronoUnit.MICROS);
    private static final Instant T1 = Instant.parse("2026-10-18T12:30:00Z").truncatedTo(ChronoUnit.MICROS);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JpaLocalPokemonRepositoryAdapter adapter;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM local_pokemon");
    }

    @Test
    void savesAndReadsBackANewRecordWithAbilitiesInOrderAndNoTags() {
        LocalPokemon saved = adapter.save(pokemon(1, "bulbasaur", List.of("overgrow", "chlorophyll"), List.of()));

        assertThat(saved.version()).isZero();
        LocalPokemon found = adapter.findById(1).orElseThrow();
        assertThat(found).isEqualTo(saved);
        assertThat(found.name()).isEqualTo("bulbasaur");
        assertThat(found.category()).isEqualTo("Seed Pokémon");
        assertThat(found.weightHectograms()).isEqualTo(69);
        assertThat(found.heightDecimetres()).isEqualTo(7);
        assertThat(found.spriteUrl()).isEqualTo("https://img/1.png");
        assertThat(found.artworkUrl()).isEqualTo("https://img/artwork/1.png");
        assertThat(found.abilities()).containsExactly("overgrow", "chlorophyll");
        assertThat(found.tags()).isEmpty();
        assertThat(found.syncedAt()).isEqualTo(T0);
        assertThat(found.updatedAt()).isEqualTo(T0);
    }

    @Test
    void returnsEmptyForAnUnknownId() {
        assertThat(adapter.findById(999)).isEmpty();
    }

    @Test
    void updatesAnExistingRecordIncrementsTheVersionAndReplacesCollections() {
        LocalPokemon first = adapter.save(pokemon(25, "pikachu", List.of("static", "lightning-rod"), List.of()));

        LocalPokemon edited = withProprietary(first, "Sparky", "Kanto", "my notes", List.of("starter", "electric"));
        LocalPokemon updated = adapter.save(new LocalPokemon(edited.id(), edited.name(), "New category",
                edited.weightHectograms(), edited.heightDecimetres(), edited.spriteUrl(), edited.artworkUrl(),
                List.of("lightning-rod"), edited.localName(), edited.region(), edited.notes(), edited.tags(),
                T1, T1, edited.version()));

        assertThat(updated.version()).isEqualTo(first.version() + 1);
        Optional<LocalPokemon> found = adapter.findById(25);
        assertThat(found).contains(updated);
        assertThat(found.orElseThrow().category()).isEqualTo("New category");
        assertThat(found.orElseThrow().abilities()).containsExactly("lightning-rod");
        assertThat(found.orElseThrow().localName()).isEqualTo("Sparky");
        assertThat(found.orElseThrow().region()).isEqualTo("Kanto");
        assertThat(found.orElseThrow().notes()).isEqualTo("my notes");
        assertThat(found.orElseThrow().tags()).containsExactly("electric", "starter");
        assertThat(found.orElseThrow().syncedAt()).isEqualTo(T1);
    }

    @Test
    void incrementsTheVersionOnEverySave() {
        LocalPokemon stored = adapter.save(pokemon(25, "pikachu", List.of("static"), List.of()));

        LocalPokemon second = adapter.save(stored);
        LocalPokemon third = adapter.save(second);

        assertThat(stored.version()).isZero();
        assertThat(second.version()).isEqualTo(1L);
        assertThat(third.version()).isEqualTo(2L);
        assertThat(adapter.findById(25).orElseThrow().version()).isEqualTo(2L);
    }

    @Test
    void rejectsASaveWithAStaleVersion() {
        LocalPokemon stored = adapter.save(pokemon(25, "pikachu", List.of("static"), List.of()));
        adapter.save(stored);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.save(stored))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
    }

    @Test
    void pagesInIdOrderWithTotals() {
        for (int id : new int[] {3, 1, 5, 2, 4}) {
            adapter.save(pokemon(id, "pokemon-" + id, List.of("a" + id, "b" + id), List.of("t" + id)));
        }

        PageResult<LocalPokemon> first = adapter.findAll(new PageQuery(0, 2));
        PageResult<LocalPokemon> last = adapter.findAll(new PageQuery(2, 2));
        PageResult<LocalPokemon> beyond = adapter.findAll(new PageQuery(3, 2));

        assertThat(first.items()).extracting(LocalPokemon::id).containsExactly(1, 2);
        assertThat(first.totalElements()).isEqualTo(5);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(2);
        assertThat(first.items().get(0).abilities()).containsExactly("a1", "b1");
        assertThat(first.items().get(1).tags()).containsExactly("t2");
        assertThat(last.items()).extracting(LocalPokemon::id).containsExactly(5);
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.totalElements()).isEqualTo(5);
    }

    @Test
    void returnsAnEmptyPageForAnEmptyStore() {
        PageResult<LocalPokemon> page = adapter.findAll(new PageQuery(0, 20));

        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
    }

    @Test
    void storesAndReadsNullableColumnsAsNull() {
        adapter.save(new LocalPokemon(132, "ditto", null, 40, 3, null, null, List.of(),
                null, null, null, List.of(), T0, T0, null));

        LocalPokemon found = adapter.findById(132).orElseThrow();

        assertThat(found.category()).isNull();
        assertThat(found.spriteUrl()).isNull();
        assertThat(found.artworkUrl()).isNull();
        assertThat(found.abilities()).isEmpty();
        assertThat(found.localName()).isNull();
    }

    private static LocalPokemon pokemon(int id, String name, List<String> abilities, List<String> tags) {
        return new LocalPokemon(id, name, "Seed Pokémon", 69, 7, "https://img/" + id + ".png",
                "https://img/artwork/" + id + ".png", abilities, null, null, null, tags, T0, T0, null);
    }

    private static LocalPokemon withProprietary(
            LocalPokemon p, String localName, String region, String notes, List<String> tags) {
        return new LocalPokemon(p.id(), p.name(), p.category(), p.weightHectograms(), p.heightDecimetres(),
                p.spriteUrl(), p.artworkUrl(), p.abilities(), localName, region, notes, tags,
                p.syncedAt(), p.updatedAt(), p.version());
    }
}
