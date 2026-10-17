package com.example.tasks.dto;

import java.time.LocalDate;

import com.example.tasks.domain.TaskStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        @JsonProperty("due_date") LocalDate dueDate) {
}
