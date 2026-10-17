package com.ilia.pokedex.adapter.in.web.docs;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ProblemDetail;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Composed annotations for the error responses, so each endpoint lists the failures it can produce in one
 * line instead of repeating the same {@code @ApiResponse(content = ...)} block. Every error is an RFC 7807
 * {@link ProblemDetail} served as {@code application/problem+json} by {@code GlobalExceptionHandler}.
 */
public final class ProblemResponses {

    private ProblemResponses() {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @ApiResponse(responseCode = "400", description = "Invalid request: a malformed parameter or body",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public @interface BadRequest {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token (or wrong credentials)",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public @interface Unauthorized {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @ApiResponse(responseCode = "404", description = "The Pokémon does not exist",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public @interface NotFound {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @ApiResponse(responseCode = "409", description = "Conflict with the stored state",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public @interface Conflict {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @ApiResponse(responseCode = "502", description = "PokeAPI is unreachable or answered with an error",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public @interface BadGateway {
    }
}
