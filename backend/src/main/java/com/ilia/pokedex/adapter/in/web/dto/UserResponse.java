package com.ilia.pokedex.adapter.in.web.dto;

import com.ilia.pokedex.domain.model.User;

/** Public view of a user: never the password hash. */
public record UserResponse(String username, String role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.username(), user.role());
    }
}
