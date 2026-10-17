package com.ilia.pokedex.adapter.out.security;

import com.ilia.pokedex.application.port.out.PasswordHasher;

/** STUB (red step). */
public class BCryptPasswordHasher implements PasswordHasher {

    @Override
    public String hash(String rawPassword) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean matches(String rawPassword, String hash) {
        throw new UnsupportedOperationException();
    }
}
