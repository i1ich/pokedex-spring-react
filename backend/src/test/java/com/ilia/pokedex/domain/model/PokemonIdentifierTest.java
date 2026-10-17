package com.ilia.pokedex.domain.model;

import com.ilia.pokedex.domain.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PokemonIdentifierTest {

    @ParameterizedTest
    @ValueSource(strings = {"25", "pikachu", "mr-mime", "deoxys-normal", "a", "porygon2"})
    void acceptsNumericIdsAndLowercaseHyphenatedNames(String idOrName) {
        assertThat(PokemonIdentifier.requireValid(idOrName)).isEqualTo(idOrName);
    }

    @Test
    void acceptsTheMaximumLength() {
        String longest = "a".repeat(PokemonIdentifier.MAX_LENGTH);

        assertThat(PokemonIdentifier.requireValid(longest)).isEqualTo(longest);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "Pikachu", "pika chu", "pika/chu", "../etc", "mr.mime", "pikachu!", "pikachü", "pikachu\n"})
    void rejectsMalformedIdentifiersOnFieldIdOrName(String idOrName) {
        assertThatThrownBy(() -> PokemonIdentifier.requireValid(idOrName))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(e -> ((InvalidRequestException) e).field())
                .isEqualTo("idOrName");
    }

    @Test
    void rejectsIdentifiersLongerThanTheLimit() {
        assertThatThrownBy(() -> PokemonIdentifier.requireValid("a".repeat(PokemonIdentifier.MAX_LENGTH + 1)))
                .isInstanceOf(InvalidRequestException.class);
    }
}
