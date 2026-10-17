package com.ilia.pokedex.adapter.in.web;

import com.ilia.pokedex.application.port.in.GetLocalPokemonUseCase;
import com.ilia.pokedex.application.port.in.GetPokemonDetailsUseCase;
import com.ilia.pokedex.application.port.in.ListLocalPokemonUseCase;
import com.ilia.pokedex.application.port.in.ListPokemonUseCase;
import com.ilia.pokedex.application.port.in.SyncPokemonUseCase;
import com.ilia.pokedex.config.JwtProperties;
import com.ilia.pokedex.config.SecurityConfig;
import com.ilia.pokedex.domain.model.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Public versus protected routes, token validation, the problem+json shape of 401, CORS and statelessness. */
@WebMvcTest({PokemonController.class, LocalPokemonController.class})
@Import(SecurityConfig.class)
class SecurityRulesTest {

    private static final String POKEMON = "/api/v1/pokemon";
    private static final String LOCAL = "/api/v1/local-pokemon";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtProperties jwtProperties;

    @MockitoBean
    private ListPokemonUseCase listPokemon;

    @MockitoBean
    private GetPokemonDetailsUseCase getPokemonDetails;

    @MockitoBean
    private SyncPokemonUseCase syncPokemon;

    @MockitoBean
    private ListLocalPokemonUseCase listLocalPokemon;

    @MockitoBean
    private GetLocalPokemonUseCase getLocalPokemon;

    @BeforeEach
    void stubEmptyPages() {
        when(listPokemon.list(anyInt(), anyInt())).thenReturn(new PageResult<>(List.of(), 0, 20, 0));
        when(listLocalPokemon.list(anyInt(), anyInt())).thenReturn(new PageResult<>(List.of(), 0, 20, 0));
    }

    @Test
    void thePokemonCatalogueIsPublic() throws Exception {
        mvc.perform(get(POKEMON)).andExpect(status().isOk());
    }

    @Test
    void localPokemonRequiresAToken() throws Exception {
        mvc.perform(get(LOCAL))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Authentication is required to access this resource"))
                .andExpect(jsonPath("$.instance").value(LOCAL));
    }

    @ParameterizedTest
    @ValueSource(strings = {LOCAL + "/25", LOCAL + "/sync", "/api/v1/auth/me", "/api/v1/something-new"})
    void everyOtherRouteRequiresAToken(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @Test
    void writingToLocalPokemonRequiresATokenToo() throws Exception {
        mvc.perform(post(LOCAL + "/sync").contentType(MediaType.APPLICATION_JSON).content("{\"idOrName\":\"pikachu\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aValidJwtOpensLocalPokemon() throws Exception {
        mvc.perform(get(LOCAL).with(jwt())).andExpect(status().isOk());
    }

    @Test
    void writesNeedNoCsrfTokenBecauseTheApiIsStateless() throws Exception {
        // an empty id is rejected by validation (400), which proves the request got past the security chain
        mvc.perform(post(LOCAL + "/sync").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idOrName\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aRealTokenSignedWithTheConfiguredSecretIsAccepted() throws Exception {
        String token = TestJwts.valid(jwtProperties.secret(), "alice");

        mvc.perform(get(LOCAL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void anExpiredTokenIsRejected() throws Exception {
        Instant issued = Instant.now().minus(Duration.ofHours(3));
        String token = TestJwts.token(jwtProperties.secret(), "alice", List.of("USER"),
                issued, issued.plus(Duration.ofHours(1)));

        mvc.perform(get(LOCAL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("The access token is invalid or has expired"));
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRejected() throws Exception {
        String token = TestJwts.valid("a-completely-different-secret-of-32+-bytes", "alice");

        mvc.perform(get(LOCAL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void garbageInTheBearerHeaderIsRejected() throws Exception {
        mvc.perform(get(LOCAL).header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void otherAuthorisationSchemesAreNotAccepted() throws Exception {
        mvc.perform(get(LOCAL).header(HttpHeaders.AUTHORIZATION, "Basic ZGVtbzpkZW1vMTIzNDU="))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/swagger-ui.html",
            "/swagger-ui/index.html", "/v3/api-docs", "/v3/api-docs/swagger-config"})
    void healthAndApiDocumentationArePublic(String path) throws Exception {
        // not mapped in this slice, so the answer is 404 from MVC; what matters is that it is not 401/403
        MvcResult result = mvc.perform(get(path)).andReturn();

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    void otherActuatorEndpointsAreProtected() throws Exception {
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/caches")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5173", "http://localhost:3000"})
    void preflightFromTheDevAndDockerFrontendsIsAllowed(String origin) throws Exception {
        mvc.perform(options(LOCAL)
                        .header(HttpHeaders.ORIGIN, origin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        org.hamcrest.Matchers.containsString("GET")));
    }

    @Test
    void preflightFromAnotherOriginIsRefused() throws Exception {
        mvc.perform(options(LOCAL)
                        .header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void actualRequestsFromAnAllowedOriginCarryTheCorsHeader() throws Exception {
        mvc.perform(get(POKEMON).header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void theAuthenticatedRequestCreatesNoHttpSession() throws Exception {
        MvcResult result = mvc.perform(get(LOCAL).with(jwt())).andExpect(status().isOk()).andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }
}
