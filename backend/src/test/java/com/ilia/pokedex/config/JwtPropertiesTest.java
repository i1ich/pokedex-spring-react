package com.ilia.pokedex.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPropertiesTest {

    private static final String SECRET_32_BYTES = "0123456789abcdef0123456789abcdef";

    @Test
    void acceptsASecretOfExactlyThirtyTwoBytes() {
        JwtProperties properties = new JwtProperties(SECRET_32_BYTES, Duration.ofMinutes(30));

        assertThat(properties.secret()).isEqualTo(SECRET_32_BYTES);
        assertThat(properties.ttl()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void rejectsASecretShorterThanThirtyTwoBytes() {
        assertThatThrownBy(() -> new JwtProperties(SECRET_32_BYTES.substring(1), Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.jwt.secret")
                .hasMessageContaining("32 bytes");
    }

    @Test
    void measuresTheSecretInBytesNotCharacters() {
        // 16 two-byte characters are 32 bytes, 15 are 30
        assertThat(new JwtProperties("é".repeat(16), Duration.ofHours(1)).secret()).hasSize(16);
        assertThatThrownBy(() -> new JwtProperties("é".repeat(15), Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAMissingOrBlankSecret() {
        assertThatThrownBy(() -> new JwtProperties(null, Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties("   ", Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultsTheTtlToOneHour() {
        assertThat(new JwtProperties(SECRET_32_BYTES, null).ttl()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void rejectsAZeroOrNegativeTtl() {
        assertThatThrownBy(() -> new JwtProperties(SECRET_32_BYTES, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.jwt.ttl");
        assertThatThrownBy(() -> new JwtProperties(SECRET_32_BYTES, Duration.ofMinutes(-5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bindsFromConfigurationProperties() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesOnly.class)
                .withPropertyValues("app.jwt.secret=" + SECRET_32_BYTES, "app.jwt.ttl=15m")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(JwtProperties.class))
                            .isEqualTo(new JwtProperties(SECRET_32_BYTES, Duration.ofMinutes(15)));
                });
    }

    @Test
    void failsApplicationStartupWhenTheConfiguredSecretIsTooShort() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesOnly.class)
                .withPropertyValues("app.jwt.secret=too-short")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class PropertiesOnly {
    }
}
