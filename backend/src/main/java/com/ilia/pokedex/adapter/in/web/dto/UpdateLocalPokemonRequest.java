package com.ilia.pokedex.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.ilia.pokedex.application.port.in.UpdateLocalPokemonUseCase.UpdateLocalPokemonCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.io.IOException;
import java.util.List;

/**
 * Body of {@code PUT /api/v1/local-pokemon/{id}}. It replaces every proprietary field, so an omitted or
 * {@code null} field clears it. Bean Validation only checks the shape of the raw values (sizes, a version
 * is present); trimming, blank handling, the tag format and the tag count are the use case's business
 * rules.
 *
 * <p>Any other property, whether read-only ({@code id}, {@code name}, {@code stats}, {@code spriteUrl}, ...)
 * or a typo, is rejected with a 400 that names it. {@code @JsonIgnoreProperties(ignoreUnknown = false)}
 * cannot do this: the Spring Boot {@code ObjectMapper} has {@code FAIL_ON_UNKNOWN_PROPERTIES} off and an
 * {@code false} annotation only defers to it. Failing in an any-setter keeps the strictness on this DTO
 * and leaves the global setting, and so every other endpoint, untouched.
 */
public record UpdateLocalPokemonRequest(
        @Size(max = 100, message = "localName must be at most 100 characters") String localName,
        @Size(max = 100, message = "region must be at most 100 characters") String region,
        @Size(max = 1000, message = "notes must be at most 1000 characters") String notes,
        @Size(max = 10, message = "at most 10 tags are allowed")
        List<@NotNull(message = "a tag must not be null")
             @Size(max = 50, message = "a tag must be at most 50 characters") String> tags,
        @NotNull(message = "version is required")
        @PositiveOrZero(message = "version must not be negative") Long version) {

    private static final List<Object> UPDATABLE = List.of("localName", "region", "notes", "tags", "version");

    /** Called by Jackson for every property that is not one of the five above. */
    @JsonAnySetter
    public void rejectUnknownProperty(String property, Object ignored) throws IOException {
        throw new RejectedProperty(property);
    }

    public UpdateLocalPokemonCommand toCommand(int id) {
        return new UpdateLocalPokemonCommand(id, localName, region, notes, tags, version);
    }

    /**
     * Jackson's factory for {@link UnrecognizedPropertyException} needs the parser, which an any-setter does
     * not have; the constructor is protected, so this subclass supplies a parser-less instance.
     */
    private static final class RejectedProperty extends UnrecognizedPropertyException {
        RejectedProperty(String property) {
            super(null, "Unrecognized field \"" + property + "\"", null,
                    UpdateLocalPokemonRequest.class, property, UPDATABLE);
        }
    }
}
