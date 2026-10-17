package com.ilia.pokedex.adapter.in.web;

import com.ilia.pokedex.application.port.in.ListPokemonUseCase;
import com.ilia.pokedex.domain.exception.ExternalServiceException;
import com.ilia.pokedex.domain.exception.InvalidRequestException;
import com.ilia.pokedex.domain.model.PageResult;
import com.ilia.pokedex.domain.model.PokemonSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PokemonController.class)
class PokemonControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ListPokemonUseCase listPokemon;

    @Test
    void returnsAPageOfPokemonSummaries() throws Exception {
        when(listPokemon.list(1, 2)).thenReturn(new PageResult<>(List.of(
                new PokemonSummary(25, "pikachu", "https://img/25.png", "Mouse Pokémon", 60,
                        List.of("static", "lightning-rod"))), 1, 2, 1302));

        mvc.perform(get("/api/v1/pokemon").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items[0].id").value(25))
                .andExpect(jsonPath("$.items[0].name").value("pikachu"))
                .andExpect(jsonPath("$.items[0].spriteUrl").value("https://img/25.png"))
                .andExpect(jsonPath("$.items[0].category").value("Mouse Pokémon"))
                .andExpect(jsonPath("$.items[0].weightKg").value(6.0))
                .andExpect(jsonPath("$.items[0].abilities", contains("static", "lightning-rod")))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(1302))
                .andExpect(jsonPath("$.totalPages").value(651));
    }

    @Test
    void usesDefaultPaginationWhenParametersAreMissing() throws Exception {
        when(listPokemon.list(0, 20)).thenReturn(new PageResult<>(List.of(), 0, 20, 0));

        mvc.perform(get("/api/v1/pokemon"))
                .andExpect(status().isOk());

        verify(listPokemon).list(0, 20);
    }

    @Test
    void mapsBusinessValidationErrorsToProblemDetail400() throws Exception {
        when(listPokemon.list(0, 500))
                .thenThrow(new InvalidRequestException("size", "size must be between 1 and 50"));

        mvc.perform(get("/api/v1/pokemon").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].message").value("size must be between 1 and 50"));
    }

    @Test
    void rejectsNonNumericParametersWith400() throws Exception {
        mvc.perform(get("/api/v1/pokemon").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("page"));

        verify(listPokemon, never()).list(anyInt(), anyInt());
    }

    @Test
    void mapsUpstreamFailuresTo502() throws Exception {
        when(listPokemon.list(0, 20))
                .thenThrow(new ExternalServiceException("PokeAPI is unavailable", null));

        mvc.perform(get("/api/v1/pokemon"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Upstream service unavailable"));
    }
}
