package com.ilia.pokedex.adapter.out.security;

import com.ilia.pokedex.application.port.out.TokenIssuer;
import com.ilia.pokedex.domain.model.User;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.time.Clock;
import java.time.Duration;

/** STUB (red step). */
public class JwtTokenIssuer implements TokenIssuer {

    public JwtTokenIssuer(JwtEncoder encoder, Clock clock, Duration ttl) {
    }

    @Override
    public IssuedToken issue(User user) {
        throw new UnsupportedOperationException();
    }
}
