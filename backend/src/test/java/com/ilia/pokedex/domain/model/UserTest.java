package com.ilia.pokedex.domain.model;

import com.ilia.pokedex.domain.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    void normalisingTrimsAndLowerCasesTheUsername() {
        assertThat(User.normaliseUsername("  DeMo.User ")).isEqualTo("demo.user");
    }

    @Test
    void normalisingNullGivesAnEmptyUsername() {
        assertThat(User.normaliseUsername(null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "a.b-c_d9", "user_01", "007"})
    void acceptsUsernamesOfThreeToFiftyAllowedCharacters(String username) {
        assertThat(User.requireValidUsername(username)).isEqualTo(username);
    }

    @Test
    void acceptsAUsernameOfExactlyFiftyCharacters() {
        assertThat(User.requireValidUsername("x".repeat(50))).hasSize(50);
    }

    @Test
    void returnsTheNormalisedFormOfAValidUsername() {
        assertThat(User.requireValidUsername("  Ash.Ketchum  ")).isEqualTo("ash.ketchum");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "ab", "has space", "bad!", "semi;colon", "a/b", "ünï", "日本語名前"})
    void rejectsUsernamesThatBreakTheRule(String username) {
        assertThatThrownBy(() -> User.requireValidUsername(username))
                .isInstanceOfSatisfying(InvalidRequestException.class, ex -> {
                    assertThat(ex.field()).isEqualTo("username");
                    assertThat(ex.getMessage()).contains("3-50");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678", "with spaces ok", "  padded  "})
    void acceptsPasswordsOfEightToSeventyTwoBytes(String password) {
        assertThat(User.requireValidPassword(password)).isEqualTo(password);
    }

    @Test
    void acceptsAPasswordOfExactlySeventyTwoBytes() {
        assertThat(User.requireValidPassword("x".repeat(72))).hasSize(72);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1234567", "short"})
    void rejectsPasswordsShorterThanEightCharacters(String password) {
        assertThatThrownBy(() -> User.requireValidPassword(password))
                .isInstanceOfSatisfying(InvalidRequestException.class, ex -> {
                    assertThat(ex.field()).isEqualTo("password");
                    assertThat(ex.getMessage()).contains("at least 8");
                });
    }

    @Test
    void rejectsAUsernameOfFiftyOneCharacters() {
        assertThatThrownBy(() -> User.requireValidUsername("x".repeat(51)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsPasswordsLongerThanSeventyTwoBytesBecauseBcryptWouldIgnoreTheRest() {
        assertThatThrownBy(() -> User.requireValidPassword("x".repeat(73)))
                .isInstanceOfSatisfying(InvalidRequestException.class, ex -> {
                    assertThat(ex.field()).isEqualTo("password");
                    assertThat(ex.getMessage()).contains("72");
                });
    }

    @Test
    void countsBytesNotCharactersForTheUpperPasswordLimit() {
        // 37 two-byte characters are 37 chars but 74 bytes
        assertThatThrownBy(() -> User.requireValidPassword("é".repeat(37)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatCode(() -> User.requireValidPassword("é".repeat(36))).doesNotThrowAnyException();
    }
}
