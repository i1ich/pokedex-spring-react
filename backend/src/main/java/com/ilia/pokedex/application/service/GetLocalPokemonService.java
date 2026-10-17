package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.in.GetLocalPokemonUseCase;
import com.ilia.pokedex.application.port.out.LocalPokemonRepository;
import com.ilia.pokedex.domain.exception.InvalidRequestException;
import com.ilia.pokedex.domain.exception.ResourceNotFoundException;
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
        if (id < 1) {
            throw new InvalidRequestException("id", "id must be greater than or equal to 1");
        }
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Local Pokemon", id));
    }
}
