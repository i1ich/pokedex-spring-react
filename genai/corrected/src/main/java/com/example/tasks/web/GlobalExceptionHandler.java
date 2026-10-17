package com.example.tasks.web;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.example.tasks.dto.ApiFieldError;
import com.example.tasks.service.TaskNotFoundException;
import com.example.tasks.service.TaskValidationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_BASE = "https://example.com/problems/";
    private static final Comparator<ApiFieldError> ERROR_ORDER =
            Comparator.comparing(ApiFieldError::field).thenComparing(ApiFieldError::message);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleBodyValidation(MethodArgumentNotValidException ex,
                                                              HttpServletRequest request) {
        List<ApiFieldError> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(e ->
                errors.add(new ApiFieldError(toSnakeCase(e.getField()), message(e.getDefaultMessage()))));
        ex.getBindingResult().getGlobalErrors().forEach(e ->
                errors.add(new ApiFieldError(e.getObjectName(), message(e.getDefaultMessage()))));
        return validationProblem(errors, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleParameterValidation(HandlerMethodValidationException ex,
                                                                   HttpServletRequest request) {
        List<ApiFieldError> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String name = result.getMethodParameter().getParameterName();
            String field = name != null ? name : "parameter";
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                errors.add(new ApiFieldError(field, message(error.getDefaultMessage())));
            }
        }
        return validationProblem(errors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex,
                                                                   HttpServletRequest request) {
        List<ApiFieldError> errors = ex.getConstraintViolations().stream()
                .map(v -> new ApiFieldError(toSnakeCase(v.getPropertyPath().toString()), message(v.getMessage())))
                .collect(Collectors.toList());
        return validationProblem(errors, request);
    }

    @ExceptionHandler(TaskValidationException.class)
    public ResponseEntity<ProblemDetail> handleTaskValidation(TaskValidationException ex,
                                                              HttpServletRequest request) {
        return validationProblem(List.of(new ApiFieldError(ex.getField(), ex.getMessage())), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleNotReadable(HttpMessageNotReadableException ex,
                                                           HttpServletRequest request) {
        List<ApiFieldError> errors = new ArrayList<>();
        String detail = "Request body is missing or malformed JSON";
        Throwable cause = ex.getCause();
        if (cause instanceof JsonMappingException mapping) {
            String field = mapping.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .filter(name -> name != null)
                    .collect(Collectors.joining("."));
            if (!field.isEmpty()) {
                String message = "invalid value";
                if (mapping instanceof InvalidFormatException format
                        && format.getTargetType() != null
                        && format.getTargetType().isEnum()) {
                    message = "must be one of " + Arrays.toString(format.getTargetType().getEnumConstants());
                }
                errors.add(new ApiFieldError(field, message));
                detail = "Request body contains an invalid value";
            }
        }
        return problem(HttpStatus.BAD_REQUEST, "malformed-request", "Bad Request", detail, request, errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        List<ApiFieldError> errors = List.of(new ApiFieldError(ex.getName(), "invalid value"));
        return problem(HttpStatus.BAD_REQUEST, "invalid-parameter", "Bad Request",
                "Parameter '" + ex.getName() + "' has an invalid value", request, errors);
    }

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(TaskNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "task-not-found", "Task Not Found", ex.getMessage(), request, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "resource-not-found", "Not Found",
                "The requested resource was not found", request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                  HttpServletRequest request) {
        ResponseEntity<ProblemDetail> base = problem(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed",
                "Method Not Allowed", "HTTP method " + ex.getMethod() + " is not supported for this resource",
                request, null);
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(base.getHeaders());
        Set<HttpMethod> supported = ex.getSupportedHttpMethods();
        if (supported != null && !supported.isEmpty()) {
            headers.setAllow(supported);
        }
        return new ResponseEntity<>(base.getBody(), headers, base.getStatusCode());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                                     HttpServletRequest request) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type", "Unsupported Media Type",
                "Content type is not supported; use application/json", request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex,
                                                              HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "unauthorized", "Unauthorized",
                "Authentication is required to access this resource", request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "Forbidden",
                "You do not have permission to access this resource", request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex,
                                                             HttpServletRequest request) {
        log.warn("Data integrity violation on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.CONFLICT, "conflict", "Conflict",
                "The request conflicts with the current state of the resource", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal Server Error",
                "An unexpected error occurred", request, null);
    }

    private ResponseEntity<ProblemDetail> validationProblem(List<ApiFieldError> errors, HttpServletRequest request) {
        List<ApiFieldError> sorted = new ArrayList<>(errors);
        sorted.sort(ERROR_ORDER);
        return problem(HttpStatus.BAD_REQUEST, "validation-error", "Validation Failed",
                "One or more fields are invalid", request, sorted);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String slug, String title, String detail,
                                                  HttpServletRequest request, List<ApiFieldError> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + slug));
        problem.setTitle(title);
        problem.setInstance(instance(request));
        if (errors != null && !errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private URI instance(HttpServletRequest request) {
        try {
            return URI.create(request.getRequestURI());
        } catch (IllegalArgumentException e) {
            return URI.create("about:blank");
        }
    }

    private static String message(String value) {
        return value != null ? value : "invalid value";
    }

    private static String toSnakeCase(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}
