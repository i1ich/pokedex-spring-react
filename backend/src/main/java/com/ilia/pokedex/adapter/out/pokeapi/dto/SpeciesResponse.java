package com.ilia.pokedex.adapter.out.pokeapi.dto;

import java.util.List;

/** {@code GET /pokemon-species/{id}} — only the fields this service uses. */
public record SpeciesResponse(int id, String name, List<Genus> genera) {

    public record Genus(String genus, NamedApiResource language) {
    }
}
