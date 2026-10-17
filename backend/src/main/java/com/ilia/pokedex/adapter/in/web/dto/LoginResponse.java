package com.ilia.pokedex.adapter.in.web.dto;

import com.ilia.pokedex.application.port.in.AuthenticateUserUseCase.LoginResult;

import java.time.Instant;

/** Body of a successful login: the bearer token and when it expires (ISO-8601 instant). */
public record LoginResponse(String token, Instant expiresAt, String username) {

    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(result.token(), result.expiresAt(), result.username());
    }
}
