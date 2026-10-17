package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.in.GetPokemonDetailsUseCase;
import com.ilia.pokedex.application.port.out.PokemonCatalog;
import com.ilia.pokedex.domain.exception.InvalidRequestException;
import com.ilia.pokedex.domain.model.PokemonDetails;

import java.util.Objects;
import java.util.regex.Pattern;

/** US02: validates the identifier and reads one Pokémon from the external catalogue. */
public class GetPokemonDetailsService implements GetPokemonDetailsUseCase {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9-]{1," + MAX_IDENTIFIER_LENGTH + "}");

    private final PokemonCatalog catalog;

    public GetPokemonDetailsService(PokemonCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @Override
    public PokemonDetails getDetails(String idOrName) {
        validate(idOrName);
        return catalog.findDetails(idOrName);
    }

    private static void validate(String idOrName) {
        if (idOrName == null || !IDENTIFIER.matcher(idOrName).matches()) {
            throw new InvalidRequestException("idOrName",
                    "idOrName must be 1-" + MAX_IDENTIFIER_LENGTH + " lowercase letters, digits or hyphens");
        }
    }
}
