package com.ilia.pokedex.adapter.in.web;

import com.ilia.pokedex.application.port.in.DeleteLocalPokemonUseCase;
import com.ilia.pokedex.application.port.in.GetLocalPokemonUseCase;
import com.ilia.pokedex.application.port.in.ListLocalPokemonUseCase;
import com.ilia.pokedex.application.port.in.SyncPokemonUseCase;
import com.ilia.pokedex.application.port.in.UpdateLocalPokemonUseCase;
import com.ilia.pokedex.application.port.in.UpdateLocalPokemonUseCase.UpdateLocalPokemonCommand;
import com.ilia.pokedex.config.SecurityConfig;
import com.ilia.pokedex.domain.exception.ConflictException;
import com.ilia.pokedex.domain.exception.InvalidRequestException;
import com.ilia.pokedex.domain.exception.ResourceNotFoundException;
import com.ilia.pokedex.domain.model.LocalPokemon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code PUT} and {@code DELETE /api/v1/local-pokemon/{id}} (US04): body shape, error mapping, authentication.
 * Shape rules (sizes, required version, unknown fields) are decided by the DTO and tested here; the business
 * rules behind them are the service's and tested in the service tests.
 */
@WebMvcTest(LocalPokemonController.class)
@Import(SecurityConfig.class)
@WithMockUser
class LocalPokemonModificationControllerTest {

    private static final String PIKACHU = "/api/v1/local-pokemon/25";
    private static final Instant SYNCED = Instant.parse("2026-10-17T10:00:00Z");
    private static final Instant EDITED = Instant.parse("2026-10-18T12:30:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SyncPokemonUseCase syncPokemon;

    @MockitoBean
    private ListLocalPokemonUseCase listLocalPokemon;

    @MockitoBean
    private GetLocalPokemonUseCase getLocalPokemon;

    @MockitoBean
    private UpdateLocalPokemonUseCase updateLocalPokemon;

    @MockitoBean
    private DeleteLocalPokemonUseCase deleteLocalPokemon;

    // --- PUT: success -------------------------------------------------------------------------------------------

    @Test
    void answers200WithTheUpdatedRecordAndPassesTheBodyToTheUseCase() throws Exception {
        when(updateLocalPokemon.update(any())).thenReturn(edited());

        putPikachu(json("""
                {"localName":"Sparky","region":"Kanto","notes":"my notes","tags":["starter","electric"],"version":3}
                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(25))
                .andExpect(jsonPath("$.name").value("pikachu"))
                .andExpect(jsonPath("$.localName").value("Sparky"))
                .andExpect(jsonPath("$.region").value("Kanto"))
                .andExpect(jsonPath("$.notes").value("my notes"))
                .andExpect(jsonPath("$.tags", contains("electric", "starter")))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-18T12:30:00Z"))
                .andExpect(jsonPath("$.version").value(4));

        ArgumentCaptor<UpdateLocalPokemonCommand> command = ArgumentCaptor.forClass(UpdateLocalPokemonCommand.class);
        verify(updateLocalPokemon).update(command.capture());
        assertThat(command.getValue()).isEqualTo(new UpdateLocalPokemonCommand(
                25, "Sparky", "Kanto", "my notes", List.of("starter", "electric"), 3L));
    }

    @Test
    void acceptsABodyWithOnlyTheVersionToClearEverything() throws Exception {
        when(updateLocalPokemon.update(any())).thenReturn(cleared());

        putPikachu(json("{\"version\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localName").value(nullValue()));

        ArgumentCaptor<UpdateLocalPokemonCommand> command = ArgumentCaptor.forClass(UpdateLocalPokemonCommand.class);
        verify(updateLocalPokemon).update(command.capture());
        assertThat(command.getValue().localName()).isNull();
        assertThat(command.getValue().region()).isNull();
        assertThat(command.getValue().notes()).isNull();
        assertThat(command.getValue().tags()).isNullOrEmpty();
        assertThat(command.getValue().version()).isEqualTo(3L);
    }

    @Test
    void acceptsExplicitNullsAndTextAtTheLimits() throws Exception {
        when(updateLocalPokemon.update(any())).thenReturn(cleared());
        String tags = "\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\",\"h\",\"i\",\"j\"";

        putPikachu(json("{\"localName\":null,\"region\":null,\"notes\":null,\"tags\":null,\"version\":0}"))
                .andExpect(status().isOk());
        putPikachu(json("{\"localName\":\"" + "n".repeat(100) + "\",\"region\":\"" + "r".repeat(100)
                + "\",\"notes\":\"" + "x".repeat(1000) + "\",\"tags\":[" + tags + "],\"version\":0}"))
                .andExpect(status().isOk());
    }

    // --- PUT: 400 from the DTO ----------------------------------------------------------------------------------

    @Test
    void answers400WhenTheVersionIsMissingOrNegative() throws Exception {
        putPikachu(json("{\"localName\":\"Sparky\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("version"));
        putPikachu(json("{\"version\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("version"));
        putPikachu(json("{\"version\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("version"));

        verifyNoInteractions(updateLocalPokemon);
    }

    @Test
    void answers400ForTextOverTheLengthLimits() throws Exception {
        putPikachu(json("{\"localName\":\"" + "n".repeat(101) + "\",\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("localName"));
        putPikachu(json("{\"region\":\"" + "r".repeat(101) + "\",\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("region"));
        putPikachu(json("{\"notes\":\"" + "x".repeat(1001) + "\",\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("notes"));

        verifyNoInteractions(updateLocalPokemon);
    }

    @Test
    void answers400ForTooManyTagsAndForATagThatIsTooLong() throws Exception {
        putPikachu(json("{\"tags\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\",\"h\",\"i\",\"j\",\"k\"],\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("tags"));
        putPikachu(json("{\"tags\":[\"ok\",\"" + "t".repeat(51) + "\"],\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("tags[1]")));

        verifyNoInteractions(updateLocalPokemon);
    }

    // --- PUT: 400 from reading the body -------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"id", "name", "category", "weightKg", "heightM", "spriteUrl", "artworkUrl", "abilities",
            "stats", "syncedAt", "updatedAt", "locaName", "extra"})
    void answers400NamingTheFieldWhenTheBodyHasAReadOnlyOrUnknownProperty(String property) throws Exception {
        putPikachu(json("{\"version\":0,\"" + property + "\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors[0].field").value(property))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());

        verifyNoInteractions(updateLocalPokemon);
    }

    @Test
    void answers400ForMalformedJsonAndAMissingBody() throws Exception {
        putPikachu(json("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("request"));
        mvc.perform(put(PIKACHU).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        putPikachu(json("[]")).andExpect(status().isBadRequest());

        verifyNoInteractions(updateLocalPokemon);
    }

    @Test
    void answers400NamingTheFieldWhenAValueHasTheWrongType() throws Exception {
        putPikachu(json("{\"version\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("version"));
        putPikachu(json("{\"version\":0,\"tags\":\"electric\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("tags"));
        putPikachu(json("{\"version\":0,\"tags\":[\"ok\",[\"nested\"]]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("tags[1]"));

        verifyNoInteractions(updateLocalPokemon);
    }

    // --- PUT: id, business errors ------------------------------------------------------------------------------

    @Test
    void answers400ForANonNumericId() throws Exception {
        mvc.perform(put("/api/v1/local-pokemon/pikachu").contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("id"));

        verifyNoInteractions(updateLocalPokemon);
    }

    @Test
    void answers400WhenTheUseCaseRejectsTheInput() throws Exception {
        when(updateLocalPokemon.update(any())).thenThrow(
                new InvalidRequestException("localName", "localName must not be blank"));

        putPikachu(json("{\"localName\":\"  \",\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("localName"))
                .andExpect(jsonPath("$.errors[0].message").value("localName must not be blank"));
    }

    @Test
    void answers400ForAnIdBelowOne() throws Exception {
        when(updateLocalPokemon.update(any())).thenThrow(
                new InvalidRequestException("id", "id must be greater than or equal to 1"));

        mvc.perform(put("/api/v1/local-pokemon/0").contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    @Test
    void answers404ProblemDetailForAnUnknownRecord() throws Exception {
        when(updateLocalPokemon.update(any())).thenThrow(new ResourceNotFoundException("Local Pokemon", 25));

        putPikachu(json("{\"version\":0}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    void answers409ProblemDetailForAStaleVersion() throws Exception {
        when(updateLocalPokemon.update(any())).thenThrow(new ConflictException(
                "Pokémon 25 was modified by someone else (current version 3); reload and try again"));

        putPikachu(json("{\"version\":2}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value(
                        "Pokémon 25 was modified by someone else (current version 3); reload and try again"));
    }

    @Test
    @WithAnonymousUser
    void answers401ProblemDetailToAnAnonymousPut() throws Exception {
        putPikachu(json("{\"version\":0}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(updateLocalPokemon);
    }

    // --- DELETE -------------------------------------------------------------------------------------------------

    @Test
    void answers204WithNoBodyWhenTheRecordIsDeleted() throws Exception {
        mvc.perform(delete(PIKACHU))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(deleteLocalPokemon).delete(25);
    }

    @Test
    void answers404ProblemDetailWhenThereIsNothingToDelete() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Local Pokemon", 25))
                .when(deleteLocalPokemon).delete(25);

        mvc.perform(delete(PIKACHU))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void answers400ForANonNumericOrTooSmallIdOnDelete() throws Exception {
        mvc.perform(delete("/api/v1/local-pokemon/pikachu"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("id"));
        verify(deleteLocalPokemon, never()).delete(anyInt());

        org.mockito.Mockito.doThrow(new InvalidRequestException("id", "id must be greater than or equal to 1"))
                .when(deleteLocalPokemon).delete(0);
        mvc.perform(delete("/api/v1/local-pokemon/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    @Test
    @WithAnonymousUser
    void answers401ProblemDetailToAnAnonymousDelete() throws Exception {
        mvc.perform(delete(PIKACHU))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(deleteLocalPokemon);
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    private ResultActions putPikachu(String body) throws Exception {
        return mvc.perform(put(PIKACHU).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String json(String body) {
        return body.strip();
    }

    private static LocalPokemon edited() {
        return new LocalPokemon(25, "pikachu", "Mouse Pokémon", 60, 4, "https://img/25.png",
                "https://img/artwork/25.png", List.of("static"), "Sparky", "Kanto", "my notes",
                List.of("starter", "electric"), SYNCED, EDITED, 4L);
    }

    private static LocalPokemon cleared() {
        return new LocalPokemon(25, "pikachu", "Mouse Pokémon", 60, 4, "https://img/25.png",
                "https://img/artwork/25.png", List.of("static"), null, null, null, List.of(), SYNCED, EDITED, 4L);
    }
}
