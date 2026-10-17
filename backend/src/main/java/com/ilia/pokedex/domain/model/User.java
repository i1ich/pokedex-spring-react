package com.ilia.pokedex.domain.model;

import java.time.Instant;

/** STUB (red step). */
public record User(Long id, String username, String passwordHash, String role, Instant createdAt) {

    public static final String ROLE_USER = "USER";

    public static String normaliseUsername(String raw) {
        throw new UnsupportedOperationException();
    }

    public static String requireValidUsername(String raw) {
        throw new UnsupportedOperationException();
    }

    public static String requireValidPassword(String raw) {
        throw new UnsupportedOperationException();
    }
}
