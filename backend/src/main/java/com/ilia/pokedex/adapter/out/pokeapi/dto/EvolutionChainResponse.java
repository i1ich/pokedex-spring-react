package com.ilia.pokedex.adapter.out.pokeapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** {@code GET /evolution-chain/{id}} — only the fields this service uses. */
public record EvolutionChainResponse(int id, ChainLink chain) {

    /** One species in the chain; {@code evolvesTo} holds one entry per branch. */
    public record ChainLink(
            NamedApiResource species,
            @JsonProperty("evolution_details") List<EvolutionDetail> evolutionDetails,
            @JsonProperty("evolves_to") List<ChainLink> evolvesTo) {
    }

    public record EvolutionDetail(
            @JsonProperty("min_level") Integer minLevel,
            NamedApiResource trigger) {
    }
}
