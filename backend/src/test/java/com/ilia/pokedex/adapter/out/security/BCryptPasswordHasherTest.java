package com.ilia.pokedex.adapter.out.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BCryptPasswordHasherTest {

    /** The hash that {@code V2__seed_demo_user.sql} stores for the password {@code demo12345}. */
    private static final String SEEDED_DEMO_HASH = "$2a$10$g9CyLR1JLlPk7BKr3k5QteRL5cn62q3eGB2DiHBWsKRNKbjjcvbDO";

    private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

    @Test
    void producesABcryptHashThatIsNotThePassword() {
        String hash = hasher.hash("secret-pass");

        assertThat(hash).startsWith("$2").isNotEqualTo("secret-pass").hasSizeLessThanOrEqualTo(100);
    }

    @Test
    void matchesTheRightPasswordOnly() {
        String hash = hasher.hash("secret-pass");

        assertThat(hasher.matches("secret-pass", hash)).isTrue();
        assertThat(hasher.matches("Secret-pass", hash)).isFalse();
        assertThat(hasher.matches("", hash)).isFalse();
    }

    @Test
    void saltsSoTheSamePasswordHashesDifferentlyEachTime() {
        assertThat(hasher.hash("secret-pass")).isNotEqualTo(hasher.hash("secret-pass"));
    }

    @Test
    void doesNotMatchAMalformedHash() {
        assertThat(hasher.matches("secret-pass", "not-a-bcrypt-hash")).isFalse();
        assertThat(hasher.matches("secret-pass", null)).isFalse();
        assertThat(hasher.matches(null, hasher.hash("secret-pass"))).isFalse();
    }

    @Test
    void verifiesTheHashSeededForTheDemoUser() {
        assertThat(hasher.matches("demo12345", SEEDED_DEMO_HASH)).isTrue();
        assertThat(hasher.matches("demo123456", SEEDED_DEMO_HASH)).isFalse();
    }
}
