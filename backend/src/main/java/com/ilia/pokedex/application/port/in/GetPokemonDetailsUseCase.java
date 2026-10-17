package com.ilia.pokedex.application.port.in;

import com.ilia.pokedex.domain.model.PokemonDetails;

/** US02: show everything known about one Pokémon. */
public interface GetPokemonDetailsUseCase {

    int MAX_IDENTIFIER_LENGTH = 100;

    /**
     * @param idOrName PokeAPI id or name: lowercase letters, digits and {@code -}, 1 to 100 characters
     * @throws com.ilia.pokedex.domain.exception.InvalidRequestException  if the identifier is malformed
     * @throws com.ilia.pokedex.domain.exception.ResourceNotFoundException if no such Pokémon exists
     */
    PokemonDetails getDetails(String idOrName);
}
