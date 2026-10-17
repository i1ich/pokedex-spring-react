package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.in.DeleteLocalPokemonUseCase;
import com.ilia.pokedex.application.port.out.LocalPokemonRepository;
import com.ilia.pokedex.domain.exception.InvalidRequestException;
import com.ilia.pokedex.domain.exception.ResourceNotFoundException;

import java.util.Objects;

/** Validates the id and removes the record. No version is needed: a delete wins over any concurrent edit. */
public class DeleteLocalPokemonService implements DeleteLocalPokemonUseCase {

    private final LocalPokemonRepository repository;

    public DeleteLocalPokemonService(LocalPokemonRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public void delete(int id) {
        if (id < 1) {
            throw new InvalidRequestException("id", "id must be greater than or equal to 1");
        }
        if (!repository.deleteById(id)) {
            throw new ResourceNotFoundException("Local Pokemon", id);
        }
    }
}
