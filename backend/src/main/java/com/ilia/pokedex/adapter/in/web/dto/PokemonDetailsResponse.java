package com.ilia.pokedex.adapter.in.web.dto;

import java.util.List;

/** JSON shape of {@code GET /api/v1/pokemon/{idOrName}}. */
public record PokemonDetailsResponse(
        int id,
        String name,
        String imageUrl,
        double heightM,
        double weightKg,
        List<String> types,
        List<StatResponse> stats,
        String description,
        EvolutionNodeResponse evolutionChain) {

    public record StatResponse(String name, int baseStat) {
    }

    public record EvolutionNodeResponse(
            int id,
            String name,
            Integer minLevel,
            String trigger,
            List<EvolutionNodeResponse> evolvesTo) {
    }
}
