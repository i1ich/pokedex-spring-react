package com.ilia.pokedex.adapter.in.web;

import com.ilia.pokedex.application.port.in.AuthenticateUserUseCase;
import com.ilia.pokedex.application.port.in.AuthenticateUserUseCase.LoginResult;
import com.ilia.pokedex.application.port.in.RegisterUserUseCase;
import com.ilia.pokedex.config.JwtProperties;
import com.ilia.pokedex.config.SecurityConfig;
import com.ilia.pokedex.domain.exception.AuthenticationFailedException;
import com.ilia.pokedex.domain.exception.ConflictException;
import com.ilia.pokedex.domain.exception.InvalidRequestException;
import com.ilia.pokedex.domain.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String ME = "/api/v1/auth/me";
    private static final Instant NOW = Instant.parse("2026-10-17T09:30:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtProperties jwtProperties;

    @MockitoBean
    private RegisterUserUseCase registerUser;

    @MockitoBean
    private AuthenticateUserUseCase authenticateUser;

    @Test
    void registersAUserWithoutAnyTokenOrCsrfTokenAndNeverEchoesThePassword() throws Exception {
        when(registerUser.register("alice", "secret-pass")).thenReturn(new User(7L, "alice", "HASH", "USER", NOW));

        mvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"secret-pass\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void answers409WhenTheUsernameIsTaken() throws Exception {
        when(registerUser.register("alice", "secret-pass"))
                .thenThrow(new ConflictException("Username 'alice' is already taken"));

        mvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"secret-pass\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Username 'alice' is already taken"))
                .andExpect(jsonPath("$.instance").value(REGISTER));
    }

    @Test
    void answers400WithTheOffendingFieldWhenARuleIsBroken() throws Exception {
        when(registerUser.register("ab", "secret-pass"))
                .thenThrow(new InvalidRequestException("username", "username must be 3-50 characters"));

        mvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ab\",\"password\":\"secret-pass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("username"));
    }

    @Test
    void answers400ForAMalformedBody() throws Exception {
        mvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(registerUser);
    }

    @Test
    void logsInAndReturnsTokenExpiryAndUsername() throws Exception {
        when(authenticateUser.authenticate("demo", "demo12345"))
                .thenReturn(new LoginResult("jwt.value", Instant.parse("2026-10-17T10:30:00Z"), "demo"));

        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo\",\"password\":\"demo12345\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token").value("jwt.value"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-17T10:30:00Z"))
                .andExpect(jsonPath("$.username").value("demo"));
    }

    @Test
    void answers401WithAProblemDetailAndABearerChallengeForWrongCredentials() throws Exception {
        when(authenticateUser.authenticate("demo", "nope-nope"))
                .thenThrow(new AuthenticationFailedException());

        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo\",\"password\":\"nope-nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Authentication failed"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Invalid username or password"))
                .andExpect(jsonPath("$.instance").value(LOGIN));
    }

    @Test
    void passesNullFieldsOfAnIncompleteLoginBodyToTheUseCaseWhichRejectsThem() throws Exception {
        when(authenticateUser.authenticate(null, null))
                .thenThrow(new InvalidRequestException("username", "username must not be blank"));

        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("username"));
        verify(authenticateUser).authenticate(null, null);
    }

    @Test
    void meRequiresAToken() throws Exception {
        mvc.perform(get(ME))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void meReturnsTheUsernameAndRoleOfTheAuthenticatedUser() throws Exception {
        mvc.perform(get(ME).with(jwt().jwt(j -> j.subject("demo"))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("demo"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void meReadsTheRoleFromARealTokenIssuedWithTheConfiguredSecret() throws Exception {
        String token = TestJwts.valid(jwtProperties.secret(), "alice");

        mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("USER"));
    }
}
