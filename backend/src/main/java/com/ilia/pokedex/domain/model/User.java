package com.ilia.pokedex.domain.model;

import com.ilia.pokedex.domain.exception.ConflictException;
import com.ilia.pokedex.domain.exception.InvalidRequestException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A registered user. {@code id} is {@code null} until the user is stored. {@code passwordHash} is the output
 * of the {@code PasswordHasher} port, never a raw password.
 *
 * <p>Also home of the credential rules, so they are the same wherever a user is created:
 * <ul>
 *   <li>A username is trimmed and lower-cased first ({@link #normaliseUsername}); the result must be 3 to
 *       {@value #USERNAME_MAX_LENGTH} characters from {@code [a-z0-9_.-]}. Normalising makes usernames
 *       case-insensitive and keeps register and login consistent.</li>
 *   <li>A password is used exactly as typed (no trimming) and must be at least {@value #PASSWORD_MIN_LENGTH}
 *       characters and at most {@value #PASSWORD_MAX_BYTES} bytes in UTF-8, because BCrypt ignores everything
 *       after the 72nd byte.</li>
 * </ul>
 */
public record User(Long id, String username, String passwordHash, String role, Instant createdAt) {

    public static final String ROLE_USER = "USER";

    public static final int USERNAME_MIN_LENGTH = 3;
    public static final int USERNAME_MAX_LENGTH = 50;
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_BYTES = 72;

    private static final Pattern USERNAME = Pattern.compile(
            "[a-z0-9_.-]{" + USERNAME_MIN_LENGTH + "," + USERNAME_MAX_LENGTH + "}");

    public User {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Trims and lower-cases a username; {@code null} becomes the empty string. */
    public static String normaliseUsername(String raw) {
        return raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * @return the normalised username
     * @throws InvalidRequestException (field {@code username}) if it is missing or breaks the rule
     */
    public static String requireValidUsername(String raw) {
        String username = normaliseUsername(raw);
        if (!USERNAME.matcher(username).matches()) {
            throw new InvalidRequestException("username", "username must be " + USERNAME_MIN_LENGTH + "-"
                    + USERNAME_MAX_LENGTH + " characters: lowercase letters, digits, '.', '_' or '-'");
        }
        return username;
    }

    /**
     * @return the same password, for fluent use
     * @throws InvalidRequestException (field {@code password}) if it is missing, too short or too long
     */
    public static String requireValidPassword(String raw) {
        if (raw == null || raw.length() < PASSWORD_MIN_LENGTH) {
            throw new InvalidRequestException("password",
                    "password must be at least " + PASSWORD_MIN_LENGTH + " characters");
        }
        if (raw.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            throw new InvalidRequestException("password",
                    "password must be at most " + PASSWORD_MAX_BYTES + " bytes");
        }
        return raw;
    }

    /** The error for a username that is already registered (HTTP 409). */
    public static ConflictException usernameTaken(String username) {
        return new ConflictException("Username '" + username + "' is already taken");
    }
}
