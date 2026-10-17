package com.ilia.pokedex.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A Pokémon stored locally (US03): a replica of the PokeAPI data plus proprietary fields that only this
 * application owns and that a re-sync never overwrites. The id is the PokeAPI id, so syncing the same
 * Pokémon twice refers to the same record.
 *
 * @param category         English genus; {@code null} when unavailable
 * @param weightHectograms weight as PokeAPI reports it (1 hg = 0.1 kg)
 * @param heightDecimetres height as PokeAPI reports it (1 dm = 0.1 m)
 * @param abilities        ability names in PokeAPI slot order
 * @param localName        proprietary: the user's own name for the Pokémon
 * @param region           proprietary
 * @param notes            proprietary
 * @param tags             proprietary: unique and sorted
 * @param syncedAt         when the PokeAPI data was last copied
 * @param updatedAt        when the record last changed (sync or local edit)
 * @param version          optimistic-locking version; {@code null} until the record has been stored
 */
public record LocalPokemon(
        int id,
        String name,
        String category,
        int weightHectograms,
        int heightDecimetres,
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

    public LocalPokemon {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(syncedAt, "syncedAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        abilities = abilities == null ? List.of() : List.copyOf(abilities);
        tags = tags == null ? List.of() : tags.stream().distinct().sorted().toList();
    }

    /** First sync of a Pokémon: PokeAPI data only, no proprietary fields, not stored yet. */
    public static LocalPokemon firstSync(PokemonSnapshot snapshot, Instant now) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /** Re-sync: overwrites every PokeAPI field, keeps the proprietary fields and the version. */
    public LocalPokemon resyncedWith(PokemonSnapshot snapshot, Instant now) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    public double weightKg() {
        return weightHectograms / 10.0;
    }

    public double heightM() {
        return heightDecimetres / 10.0;
    }
}
