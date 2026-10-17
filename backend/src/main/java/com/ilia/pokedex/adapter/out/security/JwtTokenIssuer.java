package com.ilia.pokedex.adapter.out.security;

import com.ilia.pokedex.application.port.out.TokenIssuer;
import com.ilia.pokedex.domain.model.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Issues HS256 JWTs: {@code sub} is the username, {@code roles} a one-element list with the user's role,
 * {@code iat}/{@code exp} come from the injected clock (whole seconds, as JWT dates are) and {@code jti} is
 * random. The ttl is passed in rather than read from the config package, which adapters must not depend on.
 */
public class JwtTokenIssuer implements TokenIssuer {

    /** Name of the claim holding the user's roles; the resource server reads authorities from it. */
    public static final String ROLES_CLAIM = "roles";

    private static final JwsHeader HEADER = JwsHeader.with(MacAlgorithm.HS256).build();

    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration ttl;

    public JwtTokenIssuer(JwtEncoder encoder, Clock clock, Duration ttl) {
        this.encoder = Objects.requireNonNull(encoder, "encoder");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
    }

    @Override
    public IssuedToken issue(User user) {
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.username())
                .claim(ROLES_CLAIM, List.of(user.role()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(HEADER, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}
