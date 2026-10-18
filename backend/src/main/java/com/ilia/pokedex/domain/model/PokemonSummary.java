package com.ilia.pokedex.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * List entry for US01: sprite, artwork, category, mass and abilities ("skills").
 *
 * @param weightHectograms weight as PokeAPI reports it (1 hg = 0.1 kg)
 */
public record PokemonSummary(
        int id,
        String name,
        String spriteUrl,
        String artworkUrl,
        String category,
        int weightHectograms,
        List<String> abilities) {

    public PokemonSummary {
        Objects.requireNonNull(name, "name");
        abilities = abilities == null ? List.of() : List.copyOf(abilities);
    }

    public double weightKg() {
        return weightHectograms / 10.0;
    }
}
