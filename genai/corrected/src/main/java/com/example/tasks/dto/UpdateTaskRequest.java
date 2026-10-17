package com.example.tasks.dto;

import java.time.LocalDate;

import com.example.tasks.domain.TaskStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTaskRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @NotNull TaskStatus status,
        @JsonProperty("due_date") LocalDate dueDate) {
}
