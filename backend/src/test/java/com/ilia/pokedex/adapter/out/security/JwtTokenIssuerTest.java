package com.ilia.pokedex.adapter.out.security;

import com.ilia.pokedex.application.port.out.TokenIssuer.IssuedToken;
import com.ilia.pokedex.domain.model.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenIssuerTest {

    private static final String SECRET = "unit-test-secret-that-is-at-least-32-bytes!";
    private static final Instant NOW = Instant.parse("2026-10-17T09:30:00Z");
    private static final User ALICE = new User(3L, "alice", "HASH", "USER", NOW);

    private SecretKey key;
    private JwtTokenIssuer issuer;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        issuer = new JwtTokenIssuer(
                new NimbusJwtEncoder(new ImmutableSecret<>(key)),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofHours(1));
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        // the fixed clock is in the past, so only the signature is checked here
        nimbus.setJwtValidator(token -> OAuth2TokenValidatorResult.success());
        decoder = nimbus;
    }

    @Test
    void issuesAnHs256TokenWithSubjectRolesAndTimestamps() {
        IssuedToken issued = issuer.issue(ALICE);

        Jwt jwt = decoder.decode(issued.value());
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(jwt.getSubject()).isEqualTo("alice");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(jwt.getId()).isNotBlank();
    }

    @Test
    void reportsTheExpiryTimeItPutIntoTheToken() {
        IssuedToken issued = issuer.issue(ALICE);

        assertThat(issued.expiresAt()).isEqualTo(Instant.parse("2026-10-17T10:30:00Z"));
        assertThat(decoder.decode(issued.value()).getExpiresAt()).isEqualTo(issued.expiresAt());
    }

    @Test
    void putsTheUsersRoleIntoTheRolesClaim() {
        IssuedToken issued = issuer.issue(new User(4L, "root", "HASH", "ADMIN", NOW));

        assertThat(decoder.decode(issued.value()).getClaimAsStringList("roles")).isEqualTo(List.of("ADMIN"));
    }

    @Test
    void givesEveryTokenAUniqueId() {
        String first = decoder.decode(issuer.issue(ALICE).value()).getId();
        String second = decoder.decode(issuer.issue(ALICE).value()).getId();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void tokensSignedWithAnotherSecretAreNotAcceptedByTheDecoder() {
        SecretKey other = new SecretKeySpec(
                "another-secret-that-is-also-32-bytes-long!".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder otherDecoder = NimbusJwtDecoder.withSecretKey(other).macAlgorithm(MacAlgorithm.HS256).build();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> otherDecoder.decode(issuer.issue(ALICE).value()))
                .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
    }
}
