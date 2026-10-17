package com.ilia.pokedex.application.port.in;

import com.ilia.pokedex.domain.model.LocalPokemon;
import com.ilia.pokedex.domain.model.PageResult;

/** Browse the locally stored Pokémon page by page, ordered by id. */
public interface ListLocalPokemonUseCase {

    int DEFAULT_PAGE_SIZE = ListPokemonUseCase.DEFAULT_PAGE_SIZE;

    /**
     * @param page zero-based page index
     * @param size 1 to 50 elements
     * @throws com.ilia.pokedex.domain.exception.InvalidRequestException if page or size is out of range
     */
    PageResult<LocalPokemon> list(int page, int size);
}
