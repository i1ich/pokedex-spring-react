package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.in.SyncPokemonUseCase.SyncResult;
import com.ilia.pokedex.application.port.out.LocalPokemonRepository;
import com.ilia.pokedex.application.port.out.PokemonCatalog;
import com.ilia.pokedex.domain.exception.ExternalServiceException;
import com.ilia.pokedex.domain.exception.InvalidRequestException;
import com.ilia.pokedex.domain.exception.ResourceNotFoundException;
import com.ilia.pokedex.domain.model.LocalPokemon;
import com.ilia.pokedex.domain.model.PokemonSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SyncPokemonServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:30:00Z");
    private static final Instant EARLIER = Instant.parse("2026-10-17T10:00:00Z");

    private PokemonCatalog catalog;
    private LocalPokemonRepository repository;
    private SyncPokemonService service;

    @BeforeEach
    void setUp() {
        catalog = mock(PokemonCatalog.class);
        repository = mock(LocalPokemonRepository.class);
        when(repository.save(any(LocalPokemon.class))).thenAnswer(call -> call.getArgument(0));
        service = new SyncPokemonService(catalog, repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsTheLocalRecordOnFirstSync() {
        when(catalog.findByIdOrName("pikachu")).thenReturn(pikachu());
        when(repository.findById(25)).thenReturn(Optional.empty());

        SyncResult result = service.sync("pikachu");

        assertThat(result.created()).isTrue();
        LocalPokemon saved = savedPokemon();
        assertThat(result.pokemon()).isEqualTo(saved);
        assertThat(saved.id()).isEqualTo(25);
        assertThat(saved.name()).isEqualTo("pikachu");
        assertThat(saved.category()).isEqualTo("Mouse Pokémon");
        assertThat(saved.abilities()).containsExactly("static", "lightning-rod");
        assertThat(saved.localName()).isNull();
        assertThat(saved.tags()).isEmpty();
        assertThat(saved.syncedAt()).isEqualTo(NOW);
        assertThat(saved.updatedAt()).isEqualTo(NOW);
        assertThat(saved.version()).isNull();
    }

    @Test
    void looksTheRecordUpByTheIdReturnedByTheCatalogueNotByTheRequestedName() {
        when(catalog.findByIdOrName("pikachu")).thenReturn(pikachu());
        when(repository.findById(25)).thenReturn(Optional.empty());

        service.sync("pikachu");

        verify(repository).findById(25);
    }

    @Test
    void refreshesAnExistingRecordAndKeepsItsProprietaryFields() {
        LocalPokemon stored = new LocalPokemon(25, "pikachu", "Old category", 1, 1, "old-sprite", "old-artwork",
                List.of("old-ability"), "Sparky", "Kanto", "caught at Viridian Forest", List.of("electric", "fav"),
                EARLIER, EARLIER, 2L);
        when(catalog.findByIdOrName("25")).thenReturn(pikachu());
        when(repository.findById(25)).thenReturn(Optional.of(stored));

        SyncResult result = service.sync("25");

        assertThat(result.created()).isFalse();
        LocalPokemon saved = savedPokemon();
        assertThat(result.pokemon()).isEqualTo(saved);
        assertThat(saved.category()).isEqualTo("Mouse Pokémon");
        assertThat(saved.weightHectograms()).isEqualTo(60);
        assertThat(saved.abilities()).containsExactly("static", "lightning-rod");
        assertThat(saved.localName()).isEqualTo("Sparky");
        assertThat(saved.region()).isEqualTo("Kanto");
        assertThat(saved.notes()).isEqualTo("caught at Viridian Forest");
        assertThat(saved.tags()).containsExactly("electric", "fav");
        assertThat(saved.syncedAt()).isEqualTo(NOW);
        assertThat(saved.updatedAt()).isEqualTo(NOW);
        assertThat(saved.version()).isEqualTo(2L);
    }

    @Test
    void doesNotWriteWhenTheCatalogueDataIsUnchangedSoTheVersionStaysStable() {
        LocalPokemon stored = new LocalPokemon(25, "pikachu", "Mouse Pokémon", 60, 4, "https://img/25.png",
                "https://img/artwork/25.png", List.of("static", "lightning-rod"), "Sparky", "Kanto", null,
                List.of("fav"), EARLIER, EARLIER, 2L);
        when(catalog.findByIdOrName("25")).thenReturn(pikachu());
        when(repository.findById(25)).thenReturn(Optional.of(stored));

        SyncResult result = service.sync("25");

        assertThat(result.created()).isFalse();
        assertThat(result.pokemon()).isEqualTo(stored);
        verify(repository, never()).save(any(LocalPokemon.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "Pikachu", "pika chu", "../etc", "pikachu!"})
    void rejectsMalformedIdentifiersWithoutTouchingTheCatalogueOrTheStore(String idOrName) {
        assertThatThrownBy(() -> service.sync(idOrName))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(e -> ((InvalidRequestException) e).field())
                .isEqualTo("idOrName");

        verify(catalog, never()).findByIdOrName(anyString());
        verifyNoInteractions(repository);
    }

    @Test
    void propagatesNotFoundFromTheCatalogueAndStoresNothing() {
        when(catalog.findByIdOrName("missingno")).thenThrow(new ResourceNotFoundException("Pokemon", "missingno"));

        assertThatThrownBy(() -> service.sync("missingno"))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void propagatesUpstreamFailuresAndStoresNothing() {
        when(catalog.findByIdOrName("pikachu")).thenThrow(new ExternalServiceException("PokeAPI down", null));

        assertThatThrownBy(() -> service.sync("pikachu"))
                .isInstanceOf(ExternalServiceException.class);

        verifyNoInteractions(repository);
    }

    private LocalPokemon savedPokemon() {
        ArgumentCaptor<LocalPokemon> captor = ArgumentCaptor.forClass(LocalPokemon.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private static PokemonSnapshot pikachu() {
        return new PokemonSnapshot(25, "pikachu", "Mouse Pokémon", 60, 4,
                "https://img/25.png", "https://img/artwork/25.png", List.of("static", "lightning-rod"));
    }
}
