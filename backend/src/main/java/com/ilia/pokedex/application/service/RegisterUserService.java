package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.in.RegisterUserUseCase;
import com.ilia.pokedex.application.port.out.PasswordHasher;
import com.ilia.pokedex.application.port.out.UserRepository;
import com.ilia.pokedex.domain.model.User;

import java.time.Clock;
import java.util.Objects;

/**
 * Validates the credentials (rules in {@link User}), rejects a taken username, and stores the user with a
 * hashed password and the {@code USER} role. The existence check is a fast path; the unique constraint behind
 * {@link UserRepository#save} still decides when two registrations race.
 */
public class RegisterUserService implements RegisterUserUseCase {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final Clock clock;

    public RegisterUserService(UserRepository users, PasswordHasher hasher, Clock clock) {
        this.users = Objects.requireNonNull(users, "users");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public User register(String username, String password) {
        String normalised = User.requireValidUsername(username);
        User.requireValidPassword(password);
        if (users.existsByUsername(normalised)) {
            throw User.usernameTaken(normalised);
        }
        return users.save(new User(null, normalised, hasher.hash(password), User.ROLE_USER, clock.instant()));
    }
}
