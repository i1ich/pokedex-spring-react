package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.in.RegisterUserUseCase;
import com.ilia.pokedex.application.port.out.PasswordHasher;
import com.ilia.pokedex.application.port.out.UserRepository;
import com.ilia.pokedex.domain.model.User;

import java.time.Clock;

/** STUB (red step). */
public class RegisterUserService implements RegisterUserUseCase {

    public RegisterUserService(UserRepository users, PasswordHasher hasher, Clock clock) {
    }

    @Override
    public User register(String username, String password) {
        throw new UnsupportedOperationException();
    }
}
