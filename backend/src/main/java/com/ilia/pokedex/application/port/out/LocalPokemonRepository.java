package com.ilia.pokedex.application.port.out;

import com.ilia.pokedex.domain.model.LocalPokemon;
import com.ilia.pokedex.domain.model.PageQuery;
import com.ilia.pokedex.domain.model.PageResult;

import java.util.Optional;

/** Persistence of the locally stored Pokémon (implemented by the JPA adapter). */
public interface LocalPokemonRepository {

    Optional<LocalPokemon> findById(int id);

    /** @return the requested page ordered by id */
    PageResult<LocalPokemon> findAll(PageQuery query);

    /**
     * Inserts a record whose {@code version} is {@code null}, otherwise updates the stored record with
     * optimistic locking.
     *
     * @return the stored state, including the new version
     */
    LocalPokemon save(LocalPokemon pokemon);
}
