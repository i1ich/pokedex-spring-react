package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.out.LocalPokemonRepository;
import com.ilia.pokedex.domain.exception.InvalidRequestException;
import com.ilia.pokedex.domain.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeleteLocalPokemonServiceTest {

    private LocalPokemonRepository repository;
    private DeleteLocalPokemonService service;

    @BeforeEach
    void setUp() {
        repository = mock(LocalPokemonRepository.class);
        service = new DeleteLocalPokemonService(repository);
    }

    @Test
    void deletesAnExistingRecord() {
        when(repository.deleteById(25)).thenReturn(true);

        assertThatCode(() -> service.delete(25)).doesNotThrowAnyException();

        verify(repository).deleteById(25);
    }

    @Test
    void reportsAnUnknownRecordAsNotFound() {
        when(repository.deleteById(25)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(25)).isInstanceOf(ResourceNotFoundException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsIdsBelowOneWithoutCallingTheStore(int id) {
        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(e -> ((InvalidRequestException) e).field())
                .isEqualTo("id");

        verify(repository, never()).deleteById(anyInt());
    }
}
