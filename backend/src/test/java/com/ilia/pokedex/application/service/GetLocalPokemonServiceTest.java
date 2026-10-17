package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.out.LocalPokemonRepository;
import com.ilia.pokedex.domain.exception.InvalidRequestException;
import com.ilia.pokedex.domain.exception.ResourceNotFoundException;
import com.ilia.pokedex.domain.model.LocalPokemon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetLocalPokemonServiceTest {

    private LocalPokemonRepository repository;
    private GetLocalPokemonService service;

    @BeforeEach
    void setUp() {
        repository = mock(LocalPokemonRepository.class);
        service = new GetLocalPokemonService(repository);
    }

    @Test
    void returnsTheStoredRecord() {
        when(repository.findById(25)).thenReturn(Optional.of(pikachu()));

        assertThat(service.get(25)).isEqualTo(pikachu());
    }

    @Test
    void reportsAnUnsyncedPokemonAsNotFound() {
        when(repository.findById(25)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(25)).isInstanceOf(ResourceNotFoundException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsIdsBelowOneWithoutCallingTheStore(int id) {
        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(e -> ((InvalidRequestException) e).field())
                .isEqualTo("id");

        verify(repository, never()).findById(anyInt());
    }

    private static LocalPokemon pikachu() {
        Instant now = Instant.parse("2026-10-18T12:30:00Z");
        return new LocalPokemon(25, "pikachu", "Mouse Pokémon", 60, 4, null, null, List.of("static"),
                null, null, null, List.of(), now, now, 0L);
    }
}
