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
        return new LocalPokemon(
                snapshot.id(), snapshot.name(), snapshot.category(),
                snapshot.weightHectograms(), snapshot.heightDecimetres(),
                snapshot.spriteUrl(), snapshot.artworkUrl(), snapshot.abilities(),
                null, null, null, List.of(),
                now, now, null);
    }

    /** Whether a re-sync of {@code snapshot} would change nothing but the timestamps. */
    public boolean hasSameCatalogDataAs(PokemonSnapshot snapshot) {
        return name.equals(snapshot.name())
                && Objects.equals(category, snapshot.category())
                && weightHectograms == snapshot.weightHectograms()
                && heightDecimetres == snapshot.heightDecimetres()
                && Objects.equals(spriteUrl, snapshot.spriteUrl())
                && Objects.equals(artworkUrl, snapshot.artworkUrl())
                && abilities.equals(snapshot.abilities());
    }

    /** Re-sync: overwrites every PokeAPI field, keeps the proprietary fields and the version. */
    public LocalPokemon resyncedWith(PokemonSnapshot snapshot, Instant now) {
        return new LocalPokemon(
                id, snapshot.name(), snapshot.category(),
                snapshot.weightHectograms(), snapshot.heightDecimetres(),
                snapshot.spriteUrl(), snapshot.artworkUrl(), snapshot.abilities(),
                localName, region, notes, tags,
                now, now, version);
    }

    /** Local edit: replaces the proprietary fields only; PokeAPI data, {@code syncedAt} and version are kept. */
    public LocalPokemon withProprietaryFields(
            String localName, String region, String notes, List<String> tags, Instant now) {
        return new LocalPokemon(
                id, name, category, weightHectograms, heightDecimetres, spriteUrl, artworkUrl, abilities,
                localName, region, notes, tags,
                syncedAt, now, version);
    }

    public double weightKg() {
        return weightHectograms / 10.0;
    }

    public double heightM() {
        return heightDecimetres / 10.0;
    }
}
