package com.example.tasks.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.tasks.repository.TaskRepository;

/** The page-size cap must hold for every caller of the service, not only for the HTTP controller. */
@ExtendWith(MockitoExtension.class)
class TaskServicePagingTest {

    @Mock
    private TaskRepository taskRepository;

    private TaskService service() {
        return new TaskService(taskRepository, Clock.systemUTC());
    }

    @Test
    void sizeAboveTheMaximumIsRejected() {
        assertThatExceptionOfType(TaskValidationException.class)
                .isThrownBy(() -> service().list("alice", null, 0, 101))
                .satisfies(ex -> assertThat(ex.getField()).isEqualTo("size"));
        verifyNoInteractions(taskRepository);
    }

    @Test
    void sizeBelowOneIsRejectedAsValidationNotAsIllegalArgument() {
        assertThatExceptionOfType(TaskValidationException.class)
                .isThrownBy(() -> service().list("alice", null, 0, 0))
                .satisfies(ex -> assertThat(ex.getField()).isEqualTo("size"));
    }

    @Test
    void negativePageIsRejectedAsValidationNotAsIllegalArgument() {
        assertThatExceptionOfType(TaskValidationException.class)
                .isThrownBy(() -> service().list("alice", null, -1, 20))
                .satisfies(ex -> assertThat(ex.getField()).isEqualTo("page"));
    }
}
