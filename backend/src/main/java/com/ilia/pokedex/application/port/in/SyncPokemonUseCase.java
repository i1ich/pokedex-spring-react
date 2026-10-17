package com.ilia.pokedex.application.port.in;

import com.ilia.pokedex.domain.model.LocalPokemon;

/** US03: copy a Pokémon from PokeAPI into the local store, idempotently. */
public interface SyncPokemonUseCase {

    /**
     * Creates the local record on first sync; on later syncs refreshes the PokeAPI fields and keeps the
     * proprietary ones.
     *
     * @param idOrName PokeAPI id or name: lowercase letters, digits and {@code -}, 1 to 100 characters
     * @throws com.ilia.pokedex.domain.exception.InvalidRequestException   if the identifier is malformed
     * @throws com.ilia.pokedex.domain.exception.ResourceNotFoundException if PokeAPI has no such Pokémon
     */
    SyncResult sync(String idOrName);

    /** @param created {@code true} when the record did not exist before this sync */
    record SyncResult(LocalPokemon pokemon, boolean created) {
    }
}
