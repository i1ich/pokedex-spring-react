package com.ilia.pokedex.adapter.out.security;

import com.ilia.pokedex.application.port.out.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Objects;

/** {@link PasswordHasher} using BCrypt (cost 10, random salt per hash; the output is 60 characters). */
public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(Objects.requireNonNull(rawPassword, "rawPassword"));
    }

    /** A {@code null} password or a hash that is not BCrypt never matches. */
    @Override
    public boolean matches(String rawPassword, String hash) {
        if (rawPassword == null || hash == null) {
            return false;
        }
        return encoder.matches(rawPassword, hash);
    }
}
