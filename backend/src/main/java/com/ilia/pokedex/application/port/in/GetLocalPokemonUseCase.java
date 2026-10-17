package com.ilia.pokedex.application.port.in;

import com.ilia.pokedex.domain.model.LocalPokemon;

/** Read one locally stored Pokémon. */
public interface GetLocalPokemonUseCase {

    /**
     * @param id PokeAPI id, at least 1
     * @throws com.ilia.pokedex.domain.exception.InvalidRequestException   if {@code id < 1}
     * @throws com.ilia.pokedex.domain.exception.ResourceNotFoundException if the Pokémon was never synced
     */
    LocalPokemon get(int id);
}
