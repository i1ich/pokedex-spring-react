package com.ilia.pokedex.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/v1/local-pokemon/sync}; the identifier format is checked by the use case. */
public record SyncPokemonRequest(@NotBlank(message = "idOrName must not be blank") String idOrName) {
}
