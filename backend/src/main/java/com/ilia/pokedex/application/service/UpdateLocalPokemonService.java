package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.in.UpdateLocalPokemonUseCase;
import com.ilia.pokedex.application.port.out.LocalPokemonRepository;
import com.ilia.pokedex.domain.model.LocalPokemon;

import java.time.Clock;
import java.util.Objects;

public class UpdateLocalPokemonService implements UpdateLocalPokemonUseCase {

    public UpdateLocalPokemonService(LocalPokemonRepository repository, Clock clock) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(clock, "clock");
    }

    @Override
    public LocalPokemon update(UpdateLocalPokemonCommand command) {
        throw new UnsupportedOperationException("not implemented");
    }
}
