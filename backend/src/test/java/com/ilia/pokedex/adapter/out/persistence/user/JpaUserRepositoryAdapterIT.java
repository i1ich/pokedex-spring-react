package com.ilia.pokedex.adapter.out.persistence.user;

import com.ilia.pokedex.domain.exception.ConflictException;
import com.ilia.pokedex.domain.model.User;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the adapter against a real PostgreSQL with the Flyway schema and {@code ddl-auto: validate}, so the
 * entity mapping is checked against {@code V1__init.sql}, and checks what {@code V2__seed_demo_user.sql} left behind.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaUserRepositoryAdapter.class)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JpaUserRepositoryAdapterIT {

    // PostgreSQL stores microseconds, so test timestamps are truncated to what survives the round trip.
    private static final Instant T0 = Instant.parse("2026-10-17T10:00:00Z").truncatedTo(ChronoUnit.MICROS);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JpaUserRepositoryAdapter adapter;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void removeTestUsersButKeepTheSeededOne() {
        jdbc.update("DELETE FROM app_user WHERE username <> 'demo'");
    }

    @Test
    void savesANewUserAndAssignsAnId() {
        User saved = adapter.save(new User(null, "alice", "HASH", "USER", T0));

        assertThat(saved.id()).isNotNull().isPositive();
        assertThat(saved.username()).isEqualTo("alice");
        assertThat(saved.passwordHash()).isEqualTo("HASH");
        assertThat(saved.role()).isEqualTo("USER");
        assertThat(saved.createdAt()).isEqualTo(T0);
    }

    @Test
    void findsAUserByUsername() {
        User saved = adapter.save(new User(null, "alice", "HASH", "USER", T0));

        assertThat(adapter.findByUsername("alice")).contains(saved);
        assertThat(adapter.findByUsername("bob")).isEmpty();
    }

    @Test
    void tellsWhetherAUsernameExists() {
        adapter.save(new User(null, "alice", "HASH", "USER", T0));

        assertThat(adapter.existsByUsername("alice")).isTrue();
        assertThat(adapter.existsByUsername("bob")).isFalse();
    }

    @Test
    void translatesTheUniqueConstraintIntoAConflict() {
        adapter.save(new User(null, "alice", "HASH", "USER", T0));

        assertThatThrownBy(() -> adapter.save(new User(null, "alice", "OTHER", "USER", T0)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Username 'alice' is already taken");
        assertThat(adapter.findByUsername("alice").orElseThrow().passwordHash()).isEqualTo("HASH");
    }

    @Test
    void theMigrationSeedsTheDemoUserWithABcryptHash() {
        User demo = adapter.findByUsername("demo").orElseThrow();

        assertThat(demo.role()).isEqualTo("USER");
        assertThat(demo.passwordHash()).startsWith("$2a$10$").hasSize(60);
    }
}
