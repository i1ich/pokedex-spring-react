package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.in.SyncPokemonUseCase;
import com.ilia.pokedex.application.port.out.LocalPokemonRepository;
import com.ilia.pokedex.application.port.out.PokemonCatalog;

import java.time.Clock;
import java.util.Objects;

/** US03: reads a Pokémon from the external catalogue and upserts it into the local store. */
public class SyncPokemonService implements SyncPokemonUseCase {

    private final PokemonCatalog catalog;
    private final LocalPokemonRepository repository;
    private final Clock clock;

    public SyncPokemonService(PokemonCatalog catalog, LocalPokemonRepository repository, Clock clock) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public SyncResult sync(String idOrName) {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
