package com.ilia.pokedex.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * The PokeAPI-owned data of one Pokémon that is copied into the local store (US03): exactly the fields
 * that a sync overwrites, and nothing the local store adds itself.
 *
 * @param category         English genus of the species (e.g. "Seed Pokémon"); {@code null} when unavailable
 * @param weightHectograms weight as PokeAPI reports it (1 hg = 0.1 kg)
 * @param heightDecimetres height as PokeAPI reports it (1 dm = 0.1 m)
 * @param spriteUrl        plain front sprite; may be {@code null}
 * @param artworkUrl       official artwork; may be {@code null}
 * @param abilities        ability names in slot order
 */
public record PokemonSnapshot(
        int id,
        String name,
        String category,
        int weightHectograms,
        int heightDecimetres,
        String spriteUrl,
        String artworkUrl,
        List<String> abilities) {

    public PokemonSnapshot {
        Objects.requireNonNull(name, "name");
        abilities = abilities == null ? List.of() : List.copyOf(abilities);
    }
}
