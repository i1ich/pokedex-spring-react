package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.out.LocalPokemonRepository;
import com.ilia.pokedex.domain.exception.InvalidRequestException;
import com.ilia.pokedex.domain.model.LocalPokemon;
import com.ilia.pokedex.domain.model.PageQuery;
import com.ilia.pokedex.domain.model.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListLocalPokemonServiceTest {

    private LocalPokemonRepository repository;
    private ListLocalPokemonService service;

    @BeforeEach
    void setUp() {
        repository = mock(LocalPokemonRepository.class);
        service = new ListLocalPokemonService(repository);
    }

    @Test
    void returnsTheRequestedPageFromTheStore() {
        PageResult<LocalPokemon> page = new PageResult<>(List.of(pikachu()), 2, 20, 41);
        when(repository.findAll(new PageQuery(2, 20))).thenReturn(page);

        assertThat(service.list(2, 20)).isEqualTo(page);
    }

    @Test
    void acceptsTheLargestPageSize() {
        when(repository.findAll(new PageQuery(0, 50))).thenReturn(new PageResult<>(List.of(), 0, 50, 0));

        assertThat(service.list(0, 50).items()).isEmpty();
    }

    @ParameterizedTest(name = "page={0}, size={1} -> invalid {2}")
    @CsvSource({
            "-1, 20, page",
            "0, 0, size",
            "0, -5, size",
            "0, 51, size"
    })
    void rejectsInvalidPaginationWithoutCallingTheStore(int page, int size, String field) {
        assertThatThrownBy(() -> service.list(page, size))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(e -> ((InvalidRequestException) e).field())
                .isEqualTo(field);

        verify(repository, never()).findAll(any());
    }

    private static LocalPokemon pikachu() {
        Instant now = Instant.parse("2026-10-18T12:30:00Z");
        return new LocalPokemon(25, "pikachu", "Mouse Pokémon", 60, 4, null, null, List.of("static"),
                null, null, null, List.of(), now, now, 0L);
    }
}
