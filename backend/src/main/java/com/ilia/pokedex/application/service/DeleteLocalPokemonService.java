package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.in.DeleteLocalPokemonUseCase;
import com.ilia.pokedex.application.port.out.LocalPokemonRepository;

import java.util.Objects;

public class DeleteLocalPokemonService implements DeleteLocalPokemonUseCase {

    public DeleteLocalPokemonService(LocalPokemonRepository repository) {
        Objects.requireNonNull(repository, "repository");
    }

    @Override
    public void delete(int id) {
        throw new UnsupportedOperationException("not implemented");
    }
}
