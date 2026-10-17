package com.ilia.pokedex.adapter.in.web.dto;

/**
 * Body of {@code POST /api/v1/auth/register} and {@code /login}. The rules are applied by the use cases,
 * which also reject missing fields. {@link #toString()} leaves the password out so it cannot leak into logs.
 */
public record CredentialsRequest(String username, String password) {

    @Override
    public String toString() {
        return "CredentialsRequest[username=" + username + ", password=***]";
    }
}
