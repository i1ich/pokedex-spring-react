package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.in.AuthenticateUserUseCase;
import com.ilia.pokedex.application.port.out.PasswordHasher;
import com.ilia.pokedex.application.port.out.TokenIssuer;
import com.ilia.pokedex.application.port.out.UserRepository;

/** STUB (red step). */
public class AuthenticateUserService implements AuthenticateUserUseCase {

    public AuthenticateUserService(UserRepository users, PasswordHasher hasher, TokenIssuer tokenIssuer) {
    }

    @Override
    public LoginResult authenticate(String username, String password) {
        throw new UnsupportedOperationException();
    }
}
