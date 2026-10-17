package com.example.tasks.dto;

import java.time.LocalDate;

import com.example.tasks.domain.TaskStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTaskRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        TaskStatus status,
        @JsonProperty("due_date") @FutureOrPresent LocalDate dueDate) {
}
