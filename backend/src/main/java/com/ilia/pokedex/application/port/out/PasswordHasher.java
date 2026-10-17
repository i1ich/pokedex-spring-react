package com.ilia.pokedex.application.port.out;

/** One-way password hashing. */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);
}
