package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.in.AuthenticateUserUseCase;
import com.ilia.pokedex.application.port.out.PasswordHasher;
import com.ilia.pokedex.application.port.out.TokenIssuer;
import com.ilia.pokedex.application.port.out.TokenIssuer.IssuedToken;
import com.ilia.pokedex.application.port.out.UserRepository;
import com.ilia.pokedex.domain.exception.AuthenticationFailedException;
import com.ilia.pokedex.domain.exception.InvalidRequestException;
import com.ilia.pokedex.domain.model.User;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Looks the user up by normalised username, checks the password and issues a token. An unknown user and a
 * wrong password end in the same {@link AuthenticationFailedException}. For an unknown user the password is
 * still compared against a throw-away hash, so both failures cost one hash comparison and response time
 * does not reveal which usernames exist. The username format is not re-validated here: a name that could
 * never have been registered is simply unknown.
 */
public class AuthenticateUserService implements AuthenticateUserUseCase {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final TokenIssuer tokenIssuer;
    private final String unknownUserHash;

    public AuthenticateUserService(UserRepository users, PasswordHasher hasher, TokenIssuer tokenIssuer) {
        this.users = Objects.requireNonNull(users, "users");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.tokenIssuer = Objects.requireNonNull(tokenIssuer, "tokenIssuer");
        this.unknownUserHash = hasher.hash(UUID.randomUUID().toString());
    }

    @Override
    public LoginResult authenticate(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new InvalidRequestException("username", "username must not be blank");
        }
        if (password == null || password.isEmpty()) {
            throw new InvalidRequestException("password", "password must not be blank");
        }

        Optional<User> user = users.findByUsername(User.normaliseUsername(username));
        boolean passwordMatches = hasher.matches(password, user.map(User::passwordHash).orElse(unknownUserHash));
        if (user.isEmpty() || !passwordMatches) {
            throw new AuthenticationFailedException();
        }

        IssuedToken token = tokenIssuer.issue(user.get());
        return new LoginResult(token.value(), token.expiresAt(), user.get().username());
    }
}
