package com.ilia.pokedex.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Demo-data settings ({@code app.seed.*}). Whether seeding runs at all is decided by
 * {@code app.seed.enabled} (default {@code true}, {@code false} in tests) on the seeder bean in
 * {@link SeedConfig}.
 *
 * @param pokemonIds PokeAPI ids to make sure are stored locally, in order; unique, at least 1.
 *                   Pokémon #1 to #12 when not set or blank.
 */
@ConfigurationProperties(prefix = "app.seed")
public record SeedProperties(List<Integer> pokemonIds) {

    static final List<Integer> DEFAULT_IDS = IntStream.rangeClosed(1, 12).boxed().toList();

    public SeedProperties {
        if (pokemonIds == null || pokemonIds.isEmpty()) {
            pokemonIds = DEFAULT_IDS;
        }
        if (pokemonIds.stream().anyMatch(id -> id == null || id < 1)) {
            throw new IllegalArgumentException("app.seed.pokemon-ids must only contain ids of at least 1");
        }
        pokemonIds = pokemonIds.stream().distinct().toList();
    }
}
