package com.ilia.pokedex.application.port.in;

import com.ilia.pokedex.domain.model.PageQuery;
import com.ilia.pokedex.domain.model.PageResult;
import com.ilia.pokedex.domain.model.PokemonSummary;

/** US01: browse Pokémon page by page. */
public interface ListPokemonUseCase {

    int DEFAULT_PAGE_SIZE = 20;
    int MAX_PAGE_SIZE = PageQuery.MAX_SIZE;

    int MAX_QUERY_LENGTH = 50;

    PageResult<PokemonSummary> list(int page, int size);

    /** Pokémon whose name contains {@code query} (case-insensitive); a blank query lists everything. */
    PageResult<PokemonSummary> search(String query, int page, int size);
}
