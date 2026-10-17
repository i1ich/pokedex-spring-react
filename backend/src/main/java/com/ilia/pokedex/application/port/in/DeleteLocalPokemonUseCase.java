package com.ilia.pokedex.application.port.in;

/** Remove a locally stored Pokémon (the PokeAPI data is untouched and can be synced again). */
public interface DeleteLocalPokemonUseCase {

    /**
     * @param id PokeAPI id, at least 1
     * @throws com.ilia.pokedex.domain.exception.InvalidRequestException   if {@code id < 1}
     * @throws com.ilia.pokedex.domain.exception.ResourceNotFoundException if there is no such record
     */
    void delete(int id);
}
