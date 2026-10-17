package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.out.PasswordHasher;
import com.ilia.pokedex.application.port.out.UserRepository;
import com.ilia.pokedex.domain.exception.ConflictException;
import com.ilia.pokedex.domain.exception.InvalidRequestException;
import com.ilia.pokedex.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T09:30:00Z");

    @Mock
    private UserRepository users;

    @Mock
    private PasswordHasher hasher;

    private RegisterUserService service;

    @BeforeEach
    void setUp() {
        service = new RegisterUserService(users, hasher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void storesTheNormalisedUserWithAHashedPasswordTheUserRoleAndTheCurrentTime() {
        when(users.existsByUsername("alice")).thenReturn(false);
        when(hasher.hash("secret-pass")).thenReturn("HASHED");
        when(users.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return new User(7L, u.username(), u.passwordHash(), u.role(), u.createdAt());
        });

        User registered = service.register("  Alice ", "secret-pass");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue()).isEqualTo(new User(null, "alice", "HASHED", "USER", NOW));
        assertThat(registered.id()).isEqualTo(7L);
        assertThat(registered.username()).isEqualTo("alice");
    }

    @Test
    void neverStoresTheRawPassword() {
        when(hasher.hash("secret-pass")).thenReturn("HASHED");
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User registered = service.register("alice", "secret-pass");

        assertThat(registered.passwordHash()).isEqualTo("HASHED").isNotEqualTo("secret-pass");
    }

    @Test
    void rejectsADuplicateUsernameWithoutHashingOrSaving() {
        when(users.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> service.register("alice", "secret-pass"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Username 'alice' is already taken");

        verifyNoInteractions(hasher);
        verify(users, never()).save(any());
    }

    @Test
    void comparesUsernamesCaseInsensitivelyBecauseTheyAreNormalised() {
        when(users.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> service.register("ALICE", "secret-pass"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void reportsAConflictRaisedByTheRepositoryWhenTwoRegistrationsRace() {
        when(hasher.hash("secret-pass")).thenReturn("HASHED");
        when(users.save(any(User.class))).thenThrow(new ConflictException("Username 'alice' is already taken"));

        assertThatThrownBy(() -> service.register("alice", "secret-pass"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsAnInvalidUsernameBeforeTouchingAnyPort() {
        assertThatThrownBy(() -> service.register("a b", "secret-pass"))
                .isInstanceOfSatisfying(InvalidRequestException.class, ex -> assertThat(ex.field()).isEqualTo("username"));

        verifyNoInteractions(users, hasher);
    }

    @Test
    void rejectsAMissingUsername() {
        assertThatThrownBy(() -> service.register(null, "secret-pass"))
                .isInstanceOf(InvalidRequestException.class);

        verifyNoInteractions(users, hasher);
    }

    @Test
    void rejectsAShortPasswordBeforeTouchingAnyPort() {
        assertThatThrownBy(() -> service.register("alice", "short"))
                .isInstanceOfSatisfying(InvalidRequestException.class, ex -> assertThat(ex.field()).isEqualTo("password"));

        verifyNoInteractions(users, hasher);
    }

    @Test
    void rejectsAMissingPassword() {
        assertThatThrownBy(() -> service.register("alice", null))
                .isInstanceOf(InvalidRequestException.class);

        verifyNoInteractions(users, hasher);
    }
}
