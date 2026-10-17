package com.ilia.pokedex.application.port.in;

import java.time.Instant;

/** Check a username and password and hand out an access token. */
public interface AuthenticateUserUseCase {

    /**
     * @param username raw username; normalised like on registration
     * @param password raw password
     * @throws com.ilia.pokedex.domain.exception.InvalidRequestException       if either value is missing or blank
     * @throws com.ilia.pokedex.domain.exception.AuthenticationFailedException if the user is unknown or the password is wrong
     */
    LoginResult authenticate(String username, String password);

    /** The token, the instant it stops being valid, and the normalised username it was issued for. */
    record LoginResult(String token, Instant expiresAt, String username) {
    }
}
