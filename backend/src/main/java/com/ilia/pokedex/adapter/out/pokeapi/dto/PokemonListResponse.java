package com.ilia.pokedex.adapter.out.pokeapi.dto;

import java.util.List;

/** {@code GET /pokemon?offset=&limit=} */
public record PokemonListResponse(long count, List<NamedApiResource> results) {
}
