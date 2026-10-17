package com.example.tasks.mapper;

import com.example.tasks.domain.Task;
import com.example.tasks.dto.TaskResponse;

public final class TaskMapper {

    private TaskMapper() {
    }

    public static TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getDueDate());
    }
}
