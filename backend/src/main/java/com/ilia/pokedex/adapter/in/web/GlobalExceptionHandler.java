package com.ilia.pokedex.adapter.in.web;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.ilia.pokedex.domain.exception.AuthenticationFailedException;
import com.ilia.pokedex.domain.exception.ConflictException;
import com.ilia.pokedex.domain.exception.ExternalServiceException;
import com.ilia.pokedex.domain.exception.InvalidRequestException;
import com.ilia.pokedex.domain.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;

/**
 * Translates exceptions into RFC 7807 problem details ({@code application/problem+json}).
 * Every error has {@code type, title, status, detail, instance}; 400s add {@code errors: [{field, message}]}.
 * Extending {@link ResponseEntityExceptionHandler} covers Spring MVC's own exceptions (malformed JSON,
 * unsupported method, missing parameter, ...) with the same shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail handleInvalidRequest(InvalidRequestException ex) {
        return badRequest(ex.getMessage(), List.of(fieldError(ex.field(), ex.getMessage())));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource not found");
        return problem;
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflict");
        return problem;
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    ResponseEntity<ProblemDetail> handleAuthenticationFailed(AuthenticationFailedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Authentication failed");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(problem);
    }

    @ExceptionHandler(ExternalServiceException.class)
    ProblemDetail handleUpstream(ExternalServiceException ex) {
        log.warn("Upstream call failed: {}", ex.getMessage(), ex.getCause());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        problem.setTitle("Upstream service unavailable");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setTitle("Internal server error");
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String field = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
        String message = "'" + ex.getValue() + "' is not a valid value for " + field;
        ProblemDetail problem = badRequest(message, List.of(fieldError(field, message)));
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> fieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        ProblemDetail problem = badRequest("Request body is invalid", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Unreadable body: always a 400 with an {@code errors} entry. A property the DTO does not accept
     * ({@link UnrecognizedPropertyException}) or a value of the wrong type names the offending field
     * ({@code tags[1]} style paths); anything else (malformed JSON, no body) is reported as {@code request}.
     * Jackson's own message is never echoed, as it contains parser internals and class names.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String field = null;
        String message = "Request body is missing or is not valid JSON";
        UnrecognizedPropertyException unknown = findUnrecognizedProperty(ex);
        if (unknown != null) {
            field = unknown.getPropertyName();
            message = "'" + field + "' is read-only or unknown";
        } else if (ex.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            field = jsonPath(mismatch);
            message = field + " has the wrong type or an invalid format";
        }
        ProblemDetail problem = badRequest(message, List.of(fieldError(field, message)));
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** Jackson wraps what an any-setter throws, so the property exception can sit below the direct cause. */
    private static UnrecognizedPropertyException findUnrecognizedProperty(Throwable ex) {
        for (Throwable t = ex.getCause(); t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof UnrecognizedPropertyException unknown) {
                return unknown;
            }
        }
        return null;
    }

    /** {@code tags[1]} or {@code version}, from the path Jackson followed to the bad value. */
    private static String jsonPath(JsonMappingException ex) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference step : ex.getPath()) {
            if (step.getFieldName() != null) {
                path.append(path.isEmpty() ? "" : ".").append(step.getFieldName());
            } else if (step.getIndex() >= 0) {
                path.append('[').append(step.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    private static ProblemDetail badRequest(String detail, List<Map<String, String>> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Invalid request");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static Map<String, String> fieldError(String field, String message) {
        return Map.of(
                "field", field == null ? "request" : field,
                "message", message == null ? "invalid value" : message);
    }
}
