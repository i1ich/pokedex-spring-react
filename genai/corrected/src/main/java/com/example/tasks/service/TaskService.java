package com.example.tasks.service;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.tasks.domain.Task;
import com.example.tasks.domain.TaskStatus;
import com.example.tasks.dto.CreateTaskRequest;
import com.example.tasks.dto.PageResponse;
import com.example.tasks.dto.TaskResponse;
import com.example.tasks.dto.UpdateTaskRequest;
import com.example.tasks.mapper.TaskMapper;
import com.example.tasks.repository.TaskRepository;

@Service
public class TaskService {

    /** Upper bound on page size; the service enforces it so no caller can request an unbounded page. */
    public static final int MAX_PAGE_SIZE = 100;

    private final TaskRepository taskRepository;
    private final Clock clock;

    public TaskService(TaskRepository taskRepository, Clock clock) {
        this.taskRepository = taskRepository;
        this.clock = clock;
    }

    @Transactional
    public TaskResponse create(String owner, CreateTaskRequest request) {
        LocalDate today = LocalDate.now(clock);
        if (request.dueDate() != null && request.dueDate().isBefore(today)) {
            throw new TaskValidationException("due_date", "must not be in the past");
        }
        TaskStatus status = request.status() != null ? request.status() : TaskStatus.TODO;
        Task task = new Task(owner, request.title(), request.description(), status, request.dueDate());
        return TaskMapper.toResponse(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public TaskResponse get(String owner, Long id) {
        return TaskMapper.toResponse(findOwned(owner, id));
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> list(String owner, TaskStatus status, int page, int size) {
        if (page < 0) {
            throw new TaskValidationException("page", "must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new TaskValidationException("size", "must be between 1 and " + MAX_PAGE_SIZE);
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<Task> result = status == null
                ? taskRepository.searchByOwner(owner, pageable)
                : taskRepository.searchByOwnerAndStatus(owner, status, pageable);
        return PageResponse.from(result.map(TaskMapper::toResponse));
    }

    @Transactional
    public TaskResponse update(String owner, Long id, UpdateTaskRequest request) {
        Task task = findOwned(owner, id);
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setStatus(request.status());
        task.setDueDate(request.dueDate());
        return TaskMapper.toResponse(taskRepository.save(task));
    }

    @Transactional
    public void delete(String owner, Long id) {
        Task task = findOwned(owner, id);
        taskRepository.delete(task);
    }

    private Task findOwned(String owner, Long id) {
        return taskRepository.findByIdAndOwnerUsername(id, owner)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }
}
