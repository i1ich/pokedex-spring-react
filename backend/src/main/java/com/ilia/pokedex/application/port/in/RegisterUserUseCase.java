package com.ilia.pokedex.application.port.in;

import com.ilia.pokedex.domain.model.User;

/** Create a new user account. */
public interface RegisterUserUseCase {

    /**
     * @param username raw username; trimmed and lower-cased before it is validated and stored
     * @param password raw password, 8 to 72 bytes
     * @return the stored user (with its generated id)
     * @throws com.ilia.pokedex.domain.exception.InvalidRequestException if the username or password breaks a rule
     * @throws com.ilia.pokedex.domain.exception.ConflictException       if the username is already taken
     */
    User register(String username, String password);
}
