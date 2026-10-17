package com.ilia.pokedex.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalPokemonTest {

    private static final Instant FIRST = Instant.parse("2026-10-17T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-18T12:30:00Z");

    @Test
    void firstSyncCopiesTheCatalogueDataAndLeavesProprietaryFieldsEmpty() {
        LocalPokemon pokemon = LocalPokemon.firstSync(bulbasaur(), FIRST);

        assertThat(pokemon.id()).isEqualTo(1);
        assertThat(pokemon.name()).isEqualTo("bulbasaur");
        assertThat(pokemon.category()).isEqualTo("Seed Pokémon");
        assertThat(pokemon.weightHectograms()).isEqualTo(69);
        assertThat(pokemon.heightDecimetres()).isEqualTo(7);
        assertThat(pokemon.spriteUrl()).isEqualTo("https://img/1.png");
        assertThat(pokemon.artworkUrl()).isEqualTo("https://img/artwork/1.png");
        assertThat(pokemon.abilities()).containsExactly("overgrow", "chlorophyll");
        assertThat(pokemon.localName()).isNull();
        assertThat(pokemon.region()).isNull();
        assertThat(pokemon.notes()).isNull();
        assertThat(pokemon.tags()).isEmpty();
        assertThat(pokemon.syncedAt()).isEqualTo(FIRST);
        assertThat(pokemon.updatedAt()).isEqualTo(FIRST);
        assertThat(pokemon.version()).isNull();
    }

    @Test
    void resyncOverwritesCatalogueFieldsButKeepsProprietaryFieldsAndVersion() {
        LocalPokemon stored = new LocalPokemon(1, "bulbasaur-old", "Old category", 1, 1, "old-sprite", "old-artwork",
                List.of("old-ability"), "Bulby", "Kanto", "my favourite", List.of("grass", "starter"),
                FIRST, FIRST, 3L);

        LocalPokemon refreshed = stored.resyncedWith(bulbasaur(), LATER);

        assertThat(refreshed.id()).isEqualTo(1);
        assertThat(refreshed.name()).isEqualTo("bulbasaur");
        assertThat(refreshed.category()).isEqualTo("Seed Pokémon");
        assertThat(refreshed.weightHectograms()).isEqualTo(69);
        assertThat(refreshed.heightDecimetres()).isEqualTo(7);
        assertThat(refreshed.spriteUrl()).isEqualTo("https://img/1.png");
        assertThat(refreshed.artworkUrl()).isEqualTo("https://img/artwork/1.png");
        assertThat(refreshed.abilities()).containsExactly("overgrow", "chlorophyll");
        assertThat(refreshed.localName()).isEqualTo("Bulby");
        assertThat(refreshed.region()).isEqualTo("Kanto");
        assertThat(refreshed.notes()).isEqualTo("my favourite");
        assertThat(refreshed.tags()).containsExactly("grass", "starter");
        assertThat(refreshed.syncedAt()).isEqualTo(LATER);
        assertThat(refreshed.updatedAt()).isEqualTo(LATER);
        assertThat(refreshed.version()).isEqualTo(3L);
    }

    @Test
    void tagsAreUniqueAndSorted() {
        LocalPokemon pokemon = new LocalPokemon(1, "bulbasaur", null, 69, 7, null, null, List.of(),
                null, null, null, List.of("starter", "grass", "starter"), FIRST, FIRST, null);

        assertThat(pokemon.tags()).containsExactly("grass", "starter");
    }

    @Test
    void convertsUnitsToKilogramsAndMetres() {
        LocalPokemon pokemon = LocalPokemon.firstSync(bulbasaur(), FIRST);

        assertThat(pokemon.weightKg()).isEqualTo(6.9);
        assertThat(pokemon.heightM()).isEqualTo(0.7);
    }

    private static PokemonSnapshot bulbasaur() {
        return new PokemonSnapshot(1, "bulbasaur", "Seed Pokémon", 69, 7,
                "https://img/1.png", "https://img/artwork/1.png", List.of("overgrow", "chlorophyll"));
    }
}
