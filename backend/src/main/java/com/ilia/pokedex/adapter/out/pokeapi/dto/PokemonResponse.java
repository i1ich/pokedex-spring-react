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
        List<StatSlot> stats,
        List<TypeSlot> types,
        NamedApiResource species) {

    public record Sprites(
            @JsonProperty("front_default") String frontDefault,
            Other other) {
    }

    public record Other(@JsonProperty("official-artwork") Artwork officialArtwork) {
    }

    public record Artwork(@JsonProperty("front_default") String frontDefault) {
    }

    public record AbilitySlot(NamedApiResource ability, @JsonProperty("is_hidden") boolean hidden, int slot) {
    }

    public record StatSlot(@JsonProperty("base_stat") int baseStat, NamedApiResource stat) {
    }

    public record TypeSlot(int slot, NamedApiResource type) {
    }
}
