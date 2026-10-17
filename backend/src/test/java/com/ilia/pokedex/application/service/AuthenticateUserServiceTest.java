package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.in.AuthenticateUserUseCase.LoginResult;
import com.ilia.pokedex.application.port.out.PasswordHasher;
import com.ilia.pokedex.application.port.out.TokenIssuer;
import com.ilia.pokedex.application.port.out.TokenIssuer.IssuedToken;
import com.ilia.pokedex.application.port.out.UserRepository;
import com.ilia.pokedex.domain.exception.AuthenticationFailedException;
import com.ilia.pokedex.domain.exception.InvalidRequestException;
import com.ilia.pokedex.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticateUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T09:30:00Z");
    private static final Instant EXPIRES = Instant.parse("2026-10-17T10:30:00Z");
    private static final User DEMO = new User(1L, "demo", "STORED_HASH", "USER", NOW);

    @Mock
    private UserRepository users;

    @Mock
    private PasswordHasher hasher;

    @Mock
    private TokenIssuer tokenIssuer;

    private AuthenticateUserService service;

    @BeforeEach
    void setUp() {
        when(hasher.hash(org.mockito.ArgumentMatchers.anyString())).thenReturn("DUMMY_HASH");
        service = new AuthenticateUserService(users, hasher, tokenIssuer);
    }

    @Test
    void issuesATokenWhenTheCredentialsMatch() {
        when(users.findByUsername("demo")).thenReturn(Optional.of(DEMO));
        when(hasher.matches("demo12345", "STORED_HASH")).thenReturn(true);
        when(tokenIssuer.issue(DEMO)).thenReturn(new IssuedToken("jwt.value", EXPIRES));

        LoginResult result = service.authenticate("demo", "demo12345");

        assertThat(result).isEqualTo(new LoginResult("jwt.value", EXPIRES, "demo"));
    }

    @Test
    void normalisesTheUsernameLikeRegistrationDoes() {
        when(users.findByUsername("demo")).thenReturn(Optional.of(DEMO));
        when(hasher.matches("demo12345", "STORED_HASH")).thenReturn(true);
        when(tokenIssuer.issue(DEMO)).thenReturn(new IssuedToken("jwt.value", EXPIRES));

        LoginResult result = service.authenticate("  DEMO ", "demo12345");

        assertThat(result.username()).isEqualTo("demo");
    }

    @Test
    void rejectsAWrongPasswordWithoutIssuingAToken() {
        when(users.findByUsername("demo")).thenReturn(Optional.of(DEMO));
        when(hasher.matches("wrong-pass", "STORED_HASH")).thenReturn(false);

        assertThatThrownBy(() -> service.authenticate("demo", "wrong-pass"))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(tokenIssuer, never()).issue(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsAnUnknownUserButStillSpendsOneHashComparisonSoTheTimingDoesNotGiveItAway() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate("ghost", "whatever1"))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(hasher).matches("whatever1", "DUMMY_HASH");
        verifyNoInteractions(tokenIssuer);
    }

    @Test
    void answersAnUnknownUserAndAWrongPasswordWithTheSameMessage() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());
        when(users.findByUsername("demo")).thenReturn(Optional.of(DEMO));
        when(hasher.matches("wrong-pass", "STORED_HASH")).thenReturn(false);

        Throwable unknownUser = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.authenticate("ghost", "wrong-pass"));
        Throwable wrongPassword = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.authenticate("demo", "wrong-pass"));

        assertThat(unknownUser).isInstanceOf(AuthenticationFailedException.class);
        assertThat(wrongPassword).isInstanceOf(AuthenticationFailedException.class);
        assertThat(unknownUser.getMessage()).isEqualTo(wrongPassword.getMessage())
                .isEqualTo("Invalid username or password");
    }

    @Test
    void treatsAnImplausibleUsernameAsAnUnknownUserNotAsAValidationError() {
        when(users.findByUsername("not a valid name!")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate("Not A Valid Name!", "whatever1"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void rejectsAMissingOrBlankUsernameAsInvalid() {
        assertThatThrownBy(() -> service.authenticate(null, "demo12345"))
                .isInstanceOfSatisfying(InvalidRequestException.class, ex -> assertThat(ex.field()).isEqualTo("username"));
        assertThatThrownBy(() -> service.authenticate("   ", "demo12345"))
                .isInstanceOf(InvalidRequestException.class);

        verifyNoInteractions(users, tokenIssuer);
    }

    @Test
    void rejectsAMissingOrEmptyPasswordAsInvalid() {
        assertThatThrownBy(() -> service.authenticate("demo", null))
                .isInstanceOfSatisfying(InvalidRequestException.class, ex -> assertThat(ex.field()).isEqualTo("password"));
        assertThatThrownBy(() -> service.authenticate("demo", ""))
                .isInstanceOf(InvalidRequestException.class);

        verifyNoInteractions(users, tokenIssuer);
    }
}
