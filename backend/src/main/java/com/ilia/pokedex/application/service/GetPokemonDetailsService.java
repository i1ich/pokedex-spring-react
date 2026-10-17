package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.in.GetPokemonDetailsUseCase;
import com.ilia.pokedex.application.port.out.PokemonCatalog;
import com.ilia.pokedex.domain.model.PokemonDetails;

import java.util.Objects;

/** US02: validates the identifier and reads one Pokémon from the external catalogue. */
public class GetPokemonDetailsService implements GetPokemonDetailsUseCase {

    private final PokemonCatalog catalog;

    public GetPokemonDetailsService(PokemonCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @Override
    public PokemonDetails getDetails(String idOrName) {
        throw new UnsupportedOperationException("not implemented");
    }
}
