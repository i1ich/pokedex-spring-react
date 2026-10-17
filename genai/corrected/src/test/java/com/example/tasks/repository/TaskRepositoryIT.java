package com.example.tasks.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.tasks.domain.Task;
import com.example.tasks.domain.TaskStatus;

/**
 * Runs the real Flyway migration on PostgreSQL with ddl-auto=validate, then exercises the queries.
 * The AI-generated tests mock the repository, so none of this was ever executed by them.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class TaskRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TaskRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void users() {
        jdbc.update("insert into users (username) values ('alice'), ('bob') on conflict do nothing");
    }

    private Task save(String owner, String title, TaskStatus status, LocalDate due) {
        return repository.saveAndFlush(new Task(owner, title, null, status, due));
    }

    @Test
    void lookupByIdIsScopedToTheOwner() {
        Task alices = save("alice", "mine", TaskStatus.TODO, null);

        assertThat(repository.findByIdAndOwnerUsername(alices.getId(), "alice")).isPresent();
        assertThat(repository.findByIdAndOwnerUsername(alices.getId(), "bob")).isEmpty();
    }

    @Test
    void listIsScopedToTheOwnerAndSortedByDueDateNullsLastThenId() {
        Task noDate = save("alice", "no date", TaskStatus.TODO, null);
        Task late = save("alice", "late", TaskStatus.TODO, LocalDate.of(2999, 12, 31));
        Task earlyB = save("alice", "early b", TaskStatus.TODO, LocalDate.of(2999, 1, 1));
        Task earlyA = save("alice", "early a", TaskStatus.TODO, LocalDate.of(2999, 1, 1));
        save("bob", "not alice's", TaskStatus.TODO, LocalDate.of(2999, 1, 1));

        var page = repository.searchByOwner("alice", PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getContent()).extracting(Task::getId)
                .containsExactly(earlyB.getId(), earlyA.getId(), late.getId(), noDate.getId());
    }

    @Test
    void pagingAndStatusFilterAreAppliedInTheDatabase() {
        for (int i = 0; i < 5; i++) {
            save("alice", "todo " + i, TaskStatus.TODO, LocalDate.of(2999, 1, 1 + i));
        }
        save("alice", "done", TaskStatus.DONE, LocalDate.of(2999, 2, 1));

        var secondPage = repository.searchByOwner("alice", PageRequest.of(1, 4));
        var onlyDone = repository.searchByOwnerAndStatus("alice", TaskStatus.DONE, PageRequest.of(0, 10));

        assertThat(secondPage.getTotalElements()).isEqualTo(6);
        assertThat(secondPage.getTotalPages()).isEqualTo(2);
        assertThat(secondPage.getContent()).hasSize(2);
        assertThat(onlyDone.getContent()).extracting(Task::getTitle).isEqualTo(List.of("done"));
    }

    @Test
    void databaseRejectsABlankTitleEvenIfValidationIsBypassed() {
        assertThatThrownBy(() -> save("alice", "   ", TaskStatus.TODO, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsATaskForAnUnknownOwner() {
        assertThatThrownBy(() -> save("nobody", "orphan", TaskStatus.TODO, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
