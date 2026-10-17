package com.ilia.pokedex.adapter.in.web;

import com.ilia.pokedex.application.port.in.GetLocalPokemonUseCase;
import com.ilia.pokedex.application.port.in.ListLocalPokemonUseCase;
import com.ilia.pokedex.application.port.in.SyncPokemonUseCase;
import com.ilia.pokedex.application.port.in.SyncPokemonUseCase.SyncResult;
import com.ilia.pokedex.config.SecurityConfig;
import com.ilia.pokedex.domain.exception.ExternalServiceException;
import com.ilia.pokedex.domain.exception.InvalidRequestException;
import com.ilia.pokedex.domain.exception.ResourceNotFoundException;
import com.ilia.pokedex.domain.model.LocalPokemon;
import com.ilia.pokedex.domain.model.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Everything under {@code /api/v1/local-pokemon} is protected, so these tests run as an authenticated user. */
@WebMvcTest(LocalPokemonController.class)
@Import(SecurityConfig.class)
@WithMockUser
class LocalPokemonControllerTest {

    private static final String SYNC = "/api/v1/local-pokemon/sync";
    private static final Instant SYNCED = Instant.parse("2026-10-18T12:30:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SyncPokemonUseCase syncPokemon;

    @MockitoBean
    private ListLocalPokemonUseCase listLocalPokemon;

    @MockitoBean
    private GetLocalPokemonUseCase getLocalPokemon;

    @Test
    void answers201WithLocationAndBodyWhenThePokemonIsSyncedForTheFirstTime() throws Exception {
        when(syncPokemon.sync("pikachu")).thenReturn(new SyncResult(pikachu(), true));

        mvc.perform(post(SYNC).contentType(MediaType.APPLICATION_JSON).content("{\"idOrName\":\"pikachu\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/local-pokemon/25"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(25))
                .andExpect(jsonPath("$.name").value("pikachu"))
                .andExpect(jsonPath("$.category").value("Mouse Pokémon"))
                .andExpect(jsonPath("$.weightKg").value(6.0))
                .andExpect(jsonPath("$.heightM").value(0.4))
                .andExpect(jsonPath("$.spriteUrl").value("https://img/25.png"))
                .andExpect(jsonPath("$.artworkUrl").value("https://img/artwork/25.png"))
                .andExpect(jsonPath("$.abilities", contains("static", "lightning-rod")))
                .andExpect(jsonPath("$.localName").value(nullValue()))
                .andExpect(jsonPath("$.region").value(nullValue()))
                .andExpect(jsonPath("$.notes").value(nullValue()))
                .andExpect(jsonPath("$.tags", hasSize(0)))
                .andExpect(jsonPath("$.syncedAt").value("2026-10-18T12:30:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-18T12:30:00Z"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void answers200WithTheProprietaryFieldsWhenThePokemonWasAlreadySynced() throws Exception {
        LocalPokemon edited = new LocalPokemon(25, "pikachu", "Mouse Pokémon", 60, 4, null, null,
                List.of("static"), "Sparky", "Kanto", "my notes", List.of("electric", "fav"), SYNCED, SYNCED, 4L);
        when(syncPokemon.sync("pikachu")).thenReturn(new SyncResult(edited, false));

        mvc.perform(post(SYNC).contentType(MediaType.APPLICATION_JSON).content("{\"idOrName\":\"pikachu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localName").value("Sparky"))
                .andExpect(jsonPath("$.region").value("Kanto"))
                .andExpect(jsonPath("$.notes").value("my notes"))
                .andExpect(jsonPath("$.tags", contains("electric", "fav")))
                .andExpect(jsonPath("$.version").value(4));
    }

    @Test
    void answers400ProblemDetailWhenTheIdentifierIsMalformed() throws Exception {
        when(syncPokemon.sync("Pika Chu")).thenThrow(
                new InvalidRequestException("idOrName", "idOrName must be 1-100 lowercase letters, digits or hyphens"));

        mvc.perform(post(SYNC).contentType(MediaType.APPLICATION_JSON).content("{\"idOrName\":\"Pika Chu\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("idOrName"));
    }

    @Test
    void answers400WhenTheIdentifierIsMissingOrBlank() throws Exception {
        mvc.perform(post(SYNC).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("idOrName"));
        mvc.perform(post(SYNC).contentType(MediaType.APPLICATION_JSON).content("{\"idOrName\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("idOrName"));

        verifyNoInteractions(syncPokemon);
    }

    @Test
    void answers400ForMalformedJsonAndMissingBody() throws Exception {
        mvc.perform(post(SYNC).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post(SYNC).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(syncPokemon);
    }

    @Test
    void answers404ProblemDetailWhenPokeApiDoesNotKnowThePokemon() throws Exception {
        when(syncPokemon.sync("missingno")).thenThrow(new ResourceNotFoundException("Pokemon", "missingno"));

        mvc.perform(post(SYNC).contentType(MediaType.APPLICATION_JSON).content("{\"idOrName\":\"missingno\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    void answers502WhenPokeApiIsUnavailable() throws Exception {
        when(syncPokemon.sync("pikachu")).thenThrow(new ExternalServiceException("PokeAPI is unavailable", null));

        mvc.perform(post(SYNC).contentType(MediaType.APPLICATION_JSON).content("{\"idOrName\":\"pikachu\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Upstream service unavailable"));
    }

    @Test
    void returnsAPageOfLocalPokemon() throws Exception {
        when(listLocalPokemon.list(1, 2)).thenReturn(new PageResult<>(List.of(pikachu()), 1, 2, 3));

        mvc.perform(get("/api/v1/local-pokemon").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(25))
                .andExpect(jsonPath("$.items[0].version").value(0))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void usesDefaultPaginationWhenParametersAreMissing() throws Exception {
        when(listLocalPokemon.list(0, 20)).thenReturn(new PageResult<>(List.of(), 0, 20, 0));

        mvc.perform(get("/api/v1/local-pokemon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        verify(listLocalPokemon).list(0, 20);
    }

    @Test
    void answers400ForInvalidPagination() throws Exception {
        when(listLocalPokemon.list(0, 500))
                .thenThrow(new InvalidRequestException("size", "size must be between 1 and 50"));

        mvc.perform(get("/api/v1/local-pokemon").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        mvc.perform(get("/api/v1/local-pokemon").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("page"));
    }

    @Test
    void returnsOneLocalPokemonById() throws Exception {
        when(getLocalPokemon.get(25)).thenReturn(pikachu());

        mvc.perform(get("/api/v1/local-pokemon/25"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(25))
                .andExpect(jsonPath("$.name").value("pikachu"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void answers404ProblemDetailForAnUnsyncedId() throws Exception {
        when(getLocalPokemon.get(26)).thenThrow(new ResourceNotFoundException("Local Pokemon", 26));

        mvc.perform(get("/api/v1/local-pokemon/26"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void answers400ForNonNumericOrTooSmallIds() throws Exception {
        mvc.perform(get("/api/v1/local-pokemon/pikachu"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("id"));
        verify(getLocalPokemon, never()).get(anyInt());

        when(getLocalPokemon.get(0)).thenThrow(new InvalidRequestException("id", "id must be greater than 0"));
        mvc.perform(get("/api/v1/local-pokemon/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    private static LocalPokemon pikachu() {
        return new LocalPokemon(25, "pikachu", "Mouse Pokémon", 60, 4,
                "https://img/25.png", "https://img/artwork/25.png", List.of("static", "lightning-rod"),
                null, null, null, List.of(), SYNCED, SYNCED, 0L);
    }
}
