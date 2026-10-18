package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.out.PokemonCatalog;
import com.ilia.pokedex.domain.exception.ExternalServiceException;
import com.ilia.pokedex.domain.exception.InvalidRequestException;
import com.ilia.pokedex.domain.exception.ResourceNotFoundException;
import com.ilia.pokedex.domain.model.PokemonDetails;
import com.ilia.pokedex.domain.model.PokemonDetails.EvolutionNode;
import com.ilia.pokedex.domain.model.PokemonDetails.Stat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetPokemonDetailsServiceTest {

    private PokemonCatalog catalog;
    private GetPokemonDetailsService service;

    @BeforeEach
    void setUp() {
        catalog = mock(PokemonCatalog.class);
        service = new GetPokemonDetailsService(catalog);
    }

    @Test
    void returnsTheDetailsFoundInTheCatalogue() {
        when(catalog.findDetails("pikachu")).thenReturn(pikachu());

        PokemonDetails result = service.getDetails("pikachu");

        assertThat(result).isEqualTo(pikachu());
        verify(catalog).findDetails("pikachu");
    }

    @ParameterizedTest
    @ValueSource(strings = {"25", "pikachu", "mr-mime", "deoxys-normal", "a", "porygon2"})
    void acceptsNumericIdsAndLowercaseHyphenatedNames(String idOrName) {
        when(catalog.findDetails(idOrName)).thenReturn(pikachu());

        assertThat(service.getDetails(idOrName)).isEqualTo(pikachu());
    }

    @Test
    void acceptsTheMaximumIdentifierLength() {
        String longest = "a".repeat(100);
        when(catalog.findDetails(longest)).thenReturn(pikachu());

        assertThat(service.getDetails(longest)).isEqualTo(pikachu());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "Pikachu", "pika chu", "pika/chu", "../etc", "mr.mime", "pikachu!", "pikachü", "pikachu\n"})
    void rejectsMalformedIdentifiersWithoutCallingTheCatalogue(String idOrName) {
        assertThatThrownBy(() -> service.getDetails(idOrName))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(e -> ((InvalidRequestException) e).field())
                .isEqualTo("idOrName");

        verify(catalog, never()).findDetails(anyString());
    }

    @Test
    void rejectsIdentifiersLongerThanTheLimit() {
        assertThatThrownBy(() -> service.getDetails("a".repeat(101)))
                .isInstanceOf(InvalidRequestException.class);

        verify(catalog, never()).findDetails(anyString());
    }

    @Test
    void propagatesNotFoundFromTheCatalogue() {
        when(catalog.findDetails("missingno")).thenThrow(new ResourceNotFoundException("Pokemon", "missingno"));

        assertThatThrownBy(() -> service.getDetails("missingno"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void propagatesUpstreamFailures() {
        when(catalog.findDetails("pikachu")).thenThrow(new ExternalServiceException("PokeAPI down", null));

        assertThatThrownBy(() -> service.getDetails("pikachu"))
                .isInstanceOf(ExternalServiceException.class);
    }

    private static PokemonDetails pikachu() {
        return new PokemonDetails(25, "pikachu", "https://img/25.png", 4, 60,
                List.of("electric"),
                List.of(new Stat("hp", 35), new Stat("speed", 90)),
                "Electricity could build and cause lightning storms.",
                new EvolutionNode(172, "pichu", null, null, List.of(
                        new EvolutionNode(25, "pikachu", null, "level-up", List.of()))),
                "pikachu");
    }
}
