package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.in.ListLocalPokemonUseCase;
import com.ilia.pokedex.application.port.out.LocalPokemonRepository;
import com.ilia.pokedex.domain.model.LocalPokemon;
import com.ilia.pokedex.domain.model.PageResult;

import java.util.Objects;

/** Validates pagination and reads one page of the local store. */
public class ListLocalPokemonService implements ListLocalPokemonUseCase {

    private final LocalPokemonRepository repository;

    public ListLocalPokemonService(LocalPokemonRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public PageResult<LocalPokemon> list(int page, int size) {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
