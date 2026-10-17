package com.ilia.pokedex.adapter.in.web.dto;

import com.ilia.pokedex.domain.model.LocalPokemon;

import java.time.Instant;
import java.util.List;

/**
 * JSON shape of a locally stored Pokémon. {@code version} must be sent back by clients that update the
 * record (optimistic locking).
 */
public record LocalPokemonResponse(
        int id,
        String name,
        String category,
        double weightKg,
        double heightM,
        String spriteUrl,
        String artworkUrl,
        List<String> abilities,
        String localName,
        String region,
        String notes,
        List<String> tags,
        Instant syncedAt,
        Instant updatedAt,
        Long version) {

    public static LocalPokemonResponse from(LocalPokemon pokemon) {
        return new LocalPokemonResponse(
                pokemon.id(),
                pokemon.name(),
                pokemon.category(),
                pokemon.weightKg(),
                pokemon.heightM(),
                pokemon.spriteUrl(),
                pokemon.artworkUrl(),
                pokemon.abilities(),
                pokemon.localName(),
                pokemon.region(),
                pokemon.notes(),
                pokemon.tags(),
                pokemon.syncedAt(),
                pokemon.updatedAt(),
                pokemon.version());
    }
}
