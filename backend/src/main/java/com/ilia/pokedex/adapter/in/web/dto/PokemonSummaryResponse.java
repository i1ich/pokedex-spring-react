package com.ilia.pokedex.adapter.in.web.dto;

import com.ilia.pokedex.domain.model.PokemonSummary;

import java.util.List;

public record PokemonSummaryResponse(
        int id,
        String name,
        String spriteUrl,
        String category,
        double weightKg,
        List<String> abilities) {

    public static PokemonSummaryResponse from(PokemonSummary summary) {
        return new PokemonSummaryResponse(
                summary.id(),
                summary.name(),
                summary.spriteUrl(),
                summary.category(),
                summary.weightKg(),
                summary.abilities());
    }
}
