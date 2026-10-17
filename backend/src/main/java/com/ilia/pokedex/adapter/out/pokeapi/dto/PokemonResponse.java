package com.ilia.pokedex.adapter.out.pokeapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** {@code GET /pokemon/{idOrName}} — only the fields this service uses. */
public record PokemonResponse(
        int id,
        String name,
        int weight,
        int height,
        Sprites sprites,
        List<AbilitySlot> abilities,
        NamedApiResource species) {

    public record Sprites(@JsonProperty("front_default") String frontDefault) {
    }

    public record AbilitySlot(NamedApiResource ability, @JsonProperty("is_hidden") boolean hidden, int slot) {
    }
}
