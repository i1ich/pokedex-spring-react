package com.ilia.pokedex.application.port.out;

import com.ilia.pokedex.domain.model.User;

import java.time.Instant;

/** Creates signed access tokens. */
public interface TokenIssuer {

    IssuedToken issue(User user);

    /** The serialised token and the instant it expires. */
    record IssuedToken(String value, Instant expiresAt) {
    }
}
