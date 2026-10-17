package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.in.GetLocalPokemonUseCase;
import com.ilia.pokedex.application.port.out.LocalPokemonRepository;
import com.ilia.pokedex.domain.model.LocalPokemon;

import java.util.Objects;

/** Validates the id and reads one record of the local store. */
public class GetLocalPokemonService implements GetLocalPokemonUseCase {

    private final LocalPokemonRepository repository;

    public GetLocalPokemonService(LocalPokemonRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public LocalPokemon get(int id) {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
