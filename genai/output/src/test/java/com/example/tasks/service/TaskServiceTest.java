package com.example.tasks.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.tasks.domain.Task;
import com.example.tasks.domain.TaskStatus;
import com.example.tasks.dto.CreateTaskRequest;
import com.example.tasks.dto.PageResponse;
import com.example.tasks.dto.TaskResponse;
import com.example.tasks.dto.UpdateTaskRequest;
import com.example.tasks.repository.TaskRepository;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2025-06-15T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2025, 6, 15);

    @Mock
    private TaskRepository taskRepository;

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(taskRepository, CLOCK);
    }

    private static Task task(long id, String owner) {
        Task task = new Task(owner, "Title", "Desc", TaskStatus.TODO, TODAY.plusDays(5));
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }

    private void stubSaveAssigningId(long id) {
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", id);
            return saved;
        });
    }

    @Test
    void createDefaultsStatusToTodoAndUsesOwnerFromCaller() {
        stubSaveAssigningId(10L);
        CreateTaskRequest request = new CreateTaskRequest("Write docs", "details", null, TODAY.plusDays(1));

        TaskResponse response = service.create("alice", request);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getOwnerUsername()).isEqualTo("alice");
        assertThat(captor.getValue().getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.title()).isEqualTo("Write docs");
        assertThat(response.status()).isEqualTo(TaskStatus.TODO);
        assertThat(response.dueDate()).isEqualTo(TODAY.plusDays(1));
    }

    @Test
    void createKeepsExplicitStatus() {
        stubSaveAssigningId(11L);
        CreateTaskRequest request = new CreateTaskRequest("Ship", null, TaskStatus.IN_PROGRESS, null);

        TaskResponse response = service.create("alice", request);

        assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(response.dueDate()).isNull();
    }

    @Test
    void createAcceptsDueDateEqualToToday() {
        stubSaveAssigningId(12L);
        CreateTaskRequest request = new CreateTaskRequest("Today", null, null, TODAY);

        TaskResponse response = service.create("alice", request);

        assertThat(response.dueDate()).isEqualTo(TODAY);
    }

    @Test
    void createRejectsDueDateInThePast() {
        CreateTaskRequest request = new CreateTaskRequest("Old", null, null, TODAY.minusDays(1));

        assertThatExceptionOfType(TaskValidationException.class)
                .isThrownBy(() -> service.create("alice", request))
                .satisfies(ex -> {
                    assertThat(ex.getField()).isEqualTo("due_date");
                    assertThat(ex.getMessage()).contains("past");
                });
        verifyNoInteractions(taskRepository);
    }

    @Test
    void getReturnsTaskOwnedByCaller() {
        when(taskRepository.findByIdAndOwnerUsername(5L, "alice")).thenReturn(Optional.of(task(5L, "alice")));

        TaskResponse response = service.get("alice", 5L);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.title()).isEqualTo("Title");
    }

    @Test
    void getThrowsNotFoundWhenTaskBelongsToAnotherUserOrDoesNotExist() {
        when(taskRepository.findByIdAndOwnerUsername(5L, "mallory")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("mallory", 5L))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("5");
    }

    @Test
    void listWithoutStatusQueriesOnlyCallersTasks() {
        when(taskRepository.searchByOwner(eq("alice"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task(1L, "alice")), PageRequest.of(1, 10), 11));

        PageResponse<TaskResponse> result = service.list("alice", null, 1, 10);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(taskRepository).searchByOwner(eq("alice"), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        verify(taskRepository, never()).searchByOwnerAndStatus(any(), any(), any());
        assertThat(result.content()).hasSize(1);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(11);
        assertThat(result.totalPages()).isEqualTo(2);
    }

    @Test
    void listWithStatusUsesFilteredQuery() {
        when(taskRepository.searchByOwnerAndStatus(eq("alice"), eq(TaskStatus.DONE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PageResponse<TaskResponse> result = service.list("alice", TaskStatus.DONE, 0, 20);

        verify(taskRepository, never()).searchByOwner(any(), any());
        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void updateReplacesAllFieldsOfOwnedTask() {
        Task existing = task(7L, "alice");
        when(taskRepository.findByIdAndOwnerUsername(7L, "alice")).thenReturn(Optional.of(existing));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UpdateTaskRequest request = new UpdateTaskRequest("New title", null, TaskStatus.DONE, null);

        TaskResponse response = service.update("alice", 7L, request);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.title()).isEqualTo("New title");
        assertThat(response.description()).isNull();
        assertThat(response.status()).isEqualTo(TaskStatus.DONE);
        assertThat(response.dueDate()).isNull();
        assertThat(existing.getOwnerUsername()).isEqualTo("alice");
    }

    @Test
    void updateAllowsPastDueDate() {
        Task existing = task(8L, "alice");
        when(taskRepository.findByIdAndOwnerUsername(8L, "alice")).thenReturn(Optional.of(existing));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UpdateTaskRequest request = new UpdateTaskRequest("Late", null, TaskStatus.TODO, TODAY.minusDays(30));

        TaskResponse response = service.update("alice", 8L, request);

        assertThat(response.dueDate()).isEqualTo(TODAY.minusDays(30));
    }

    @Test
    void updateOfAnotherUsersTaskIsNotFoundAndNothingIsSaved() {
        when(taskRepository.findByIdAndOwnerUsername(7L, "mallory")).thenReturn(Optional.empty());
        UpdateTaskRequest request = new UpdateTaskRequest("Hijack", null, TaskStatus.DONE, null);

        assertThatThrownBy(() -> service.update("mallory", 7L, request))
                .isInstanceOf(TaskNotFoundException.class);
        verify(taskRepository, never()).save(any());
    }

    @Test
    void deleteRemovesOwnedTask() {
        Task existing = task(9L, "alice");
        when(taskRepository.findByIdAndOwnerUsername(9L, "alice")).thenReturn(Optional.of(existing));

        service.delete("alice", 9L);

        verify(taskRepository).delete(existing);
    }

    @Test
    void deleteOfAnotherUsersTaskIsNotFoundAndNothingIsDeleted() {
        when(taskRepository.findByIdAndOwnerUsername(9L, "mallory")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("mallory", 9L))
                .isInstanceOf(TaskNotFoundException.class);
        verify(taskRepository, never()).delete(any(Task.class));
    }
}
