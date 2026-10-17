package com.ilia.pokedex.adapter.in.web.dto;

import com.ilia.pokedex.domain.model.PokemonDetails;

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

    public static PokemonDetailsResponse from(PokemonDetails details) {
        return new PokemonDetailsResponse(
                details.id(),
                details.name(),
                details.imageUrl(),
                details.heightM(),
                details.weightKg(),
                details.types(),
                details.stats().stream().map(StatResponse::from).toList(),
                details.description(),
                EvolutionNodeResponse.from(details.evolutionChain()));
    }

    public record StatResponse(String name, int baseStat) {

        static StatResponse from(PokemonDetails.Stat stat) {
            return new StatResponse(stat.name(), stat.baseStat());
        }
    }

    /** One species in the evolution tree; branches are several entries in {@code evolvesTo}. */
    public record EvolutionNodeResponse(
            int id,
            String name,
            Integer minLevel,
            String trigger,
            List<EvolutionNodeResponse> evolvesTo) {

        static EvolutionNodeResponse from(PokemonDetails.EvolutionNode node) {
            return new EvolutionNodeResponse(
                    node.id(),
                    node.name(),
                    node.minLevel(),
                    node.trigger(),
                    node.evolvesTo().stream().map(EvolutionNodeResponse::from).toList());
        }
    }
}
