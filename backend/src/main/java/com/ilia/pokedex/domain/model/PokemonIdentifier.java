package com.ilia.pokedex.domain.model;

import com.ilia.pokedex.domain.exception.InvalidRequestException;

import java.util.regex.Pattern;

/**
 * The rule for what may be used to look a Pokémon up in the external catalogue: a PokeAPI id or a
 * lowercase name, i.e. 1 to {@value #MAX_LENGTH} lowercase letters, digits or hyphens. Shared by every
 * use case that accepts an {@code idOrName}, so the rule lives in one place.
 */
public final class PokemonIdentifier {

    public static final int MAX_LENGTH = 100;

    private static final Pattern PATTERN = Pattern.compile("[a-z0-9-]{1," + MAX_LENGTH + "}");

    private PokemonIdentifier() {
    }

    /**
     * @return the same identifier, for fluent use
     * @throws InvalidRequestException (field {@code idOrName}) if it is {@code null} or malformed
     */
    public static String requireValid(String idOrName) {
        if (idOrName == null || !PATTERN.matcher(idOrName).matches()) {
            throw new InvalidRequestException("idOrName",
                    "idOrName must be 1-" + MAX_LENGTH + " lowercase letters, digits or hyphens");
        }
        return idOrName;
    }
}
