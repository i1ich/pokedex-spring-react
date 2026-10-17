package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.in.SyncPokemonUseCase;
import com.ilia.pokedex.application.port.out.LocalPokemonRepository;
import com.ilia.pokedex.application.port.out.PokemonCatalog;

import com.ilia.pokedex.domain.model.LocalPokemon;
import com.ilia.pokedex.domain.model.PokemonIdentifier;
import com.ilia.pokedex.domain.model.PokemonSnapshot;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

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
        PokemonSnapshot snapshot = catalog.findByIdOrName(PokemonIdentifier.requireValid(idOrName));
        Instant now = clock.instant();
        Optional<LocalPokemon> existing = repository.findById(snapshot.id());

        LocalPokemon toStore = existing
                .map(stored -> stored.resyncedWith(snapshot, now))
                .orElseGet(() -> LocalPokemon.firstSync(snapshot, now));
        return new SyncResult(repository.save(toStore), existing.isEmpty());
    }
}
