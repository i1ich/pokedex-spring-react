package com.ilia.pokedex.adapter.out.pokeapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** {@code GET /pokemon-species/{id}} — only the fields this service uses. */
public record SpeciesResponse(
        int id,
        String name,
        List<Genus> genera,
        @JsonProperty("flavor_text_entries") List<FlavorText> flavorTextEntries,
        @JsonProperty("evolution_chain") NamedApiResource evolutionChain) {

    public record Genus(String genus, NamedApiResource language) {
    }

    public record FlavorText(
            @JsonProperty("flavor_text") String flavorText,
            NamedApiResource language) {
    }
}
