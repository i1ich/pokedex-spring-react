package com.ilia.pokedex.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Detail view for US02: artwork, core stats, narrative description and the evolution chain.
 *
 * @param heightDecimetres height as PokeAPI reports it (1 dm = 0.1 m)
 * @param weightHectograms weight as PokeAPI reports it (1 hg = 0.1 kg)
 * @param description      English Pokédex entry with normalised whitespace; {@code null} when unavailable
 * @param evolutionChain   root (base species) of the evolution tree; never {@code null}
 */
public record PokemonDetails(
        int id,
        String name,
        String imageUrl,
        int heightDecimetres,
        int weightHectograms,
        List<String> types,
        List<Stat> stats,
        String description,
        EvolutionNode evolutionChain) {

    public PokemonDetails {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(evolutionChain, "evolutionChain");
        types = types == null ? List.of() : List.copyOf(types);
        stats = stats == null ? List.of() : List.copyOf(stats);
    }

    public double heightM() {
        return heightDecimetres / 10.0;
    }

    public double weightKg() {
        return weightHectograms / 10.0;
    }

    /** One base stat, e.g. {@code hp = 35}. */
    public record Stat(String name, int baseStat) {
    }

    /**
     * A species in the evolution tree. Branching evolutions (Eevee) are several entries in
     * {@code evolvesTo}.
     *
     * @param id       species id
     * @param minLevel level required to reach this species, {@code null} when not level based
     * @param trigger  how this species is reached from its parent (e.g. {@code level-up},
     *                 {@code use-item}); {@code null} for the base species
     */
    public record EvolutionNode(
            int id,
            String name,
            Integer minLevel,
            String trigger,
            List<EvolutionNode> evolvesTo) {

        public EvolutionNode {
            Objects.requireNonNull(name, "name");
            evolvesTo = evolvesTo == null ? List.of() : List.copyOf(evolvesTo);
        }
    }
}
