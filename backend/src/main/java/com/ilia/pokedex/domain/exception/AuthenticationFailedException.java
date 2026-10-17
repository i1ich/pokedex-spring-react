package com.ilia.pokedex.domain.exception;

/**
 * Login failed (mapped to HTTP 401). The message is deliberately the same for an unknown user and a wrong
 * password, so the response does not reveal which usernames exist.
 */
public class AuthenticationFailedException extends DomainException {

    public static final String MESSAGE = "Invalid username or password";

    public AuthenticationFailedException() {
        super(MESSAGE);
    }
}
