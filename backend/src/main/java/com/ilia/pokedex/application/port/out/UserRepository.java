package com.ilia.pokedex.application.port.out;

import com.ilia.pokedex.domain.model.User;

import java.util.Optional;

/** Persistence of users. */
public interface UserRepository {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /**
     * Inserts a new user.
     *
     * @throws com.ilia.pokedex.domain.exception.ConflictException if the username is already stored
     */
    User save(User user);
}
