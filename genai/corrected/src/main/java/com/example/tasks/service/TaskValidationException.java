package com.example.tasks.service;

public class TaskValidationException extends RuntimeException {

    private final String field;

    public TaskValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
