package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.in.UpdateLocalPokemonUseCase.UpdateLocalPokemonCommand;
import com.ilia.pokedex.application.port.out.LocalPokemonRepository;
import com.ilia.pokedex.domain.exception.ConflictException;
import com.ilia.pokedex.domain.exception.InvalidRequestException;
import com.ilia.pokedex.domain.exception.ResourceNotFoundException;
import com.ilia.pokedex.domain.model.LocalPokemon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UpdateLocalPokemonServiceTest {

    private static final Instant SYNCED = Instant.parse("2026-10-17T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-18T12:30:00Z");

    private LocalPokemonRepository repository;
    private UpdateLocalPokemonService service;

    @BeforeEach
    void setUp() {
        repository = mock(LocalPokemonRepository.class);
        service = new UpdateLocalPokemonService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.findById(25)).thenReturn(Optional.of(stored()));
        // like the real adapter: the stored state carries the incremented version
        when(repository.save(any())).thenAnswer(call -> {
            LocalPokemon p = call.getArgument(0);
            return new LocalPokemon(p.id(), p.name(), p.category(), p.weightHectograms(), p.heightDecimetres(),
                    p.spriteUrl(), p.artworkUrl(), p.abilities(), p.localName(), p.region(), p.notes(), p.tags(),
                    p.syncedAt(), p.updatedAt(), p.version() + 1);
        });
    }

    // --- happy path -------------------------------------------------------------------------------------------

    @Test
    void replacesTheProprietaryFieldsAndLeavesEverythingElseAlone() {
        LocalPokemon result = service.update(command("Sparky", "Kanto", "my notes", List.of("starter", "electric"), 3L));

        LocalPokemon saved = savedPokemon();
        assertThat(saved.localName()).isEqualTo("Sparky");
        assertThat(saved.region()).isEqualTo("Kanto");
        assertThat(saved.notes()).isEqualTo("my notes");
        assertThat(saved.tags()).containsExactly("electric", "starter");
        assertThat(saved.updatedAt()).isEqualTo(NOW);
        assertThat(saved.syncedAt()).isEqualTo(SYNCED);
        assertThat(saved.version()).isEqualTo(3L);
        assertThat(saved).usingRecursiveComparison()
                .comparingOnlyFields("id", "name", "category", "weightHectograms", "heightDecimetres",
                        "spriteUrl", "artworkUrl", "abilities")
                .isEqualTo(stored());
        assertThat(result.version()).isEqualTo(4L);
        assertThat(result.localName()).isEqualTo("Sparky");
    }

    @Test
    void nullFieldsClearTheStoredValuesAndMissingTagsMeanNoTags() {
        service.update(command(null, null, null, null, 3L));

        LocalPokemon saved = savedPokemon();
        assertThat(saved.localName()).isNull();
        assertThat(saved.region()).isNull();
        assertThat(saved.notes()).isNull();
        assertThat(saved.tags()).isEmpty();
    }

    @Test
    void trimsTextAndTurnsBlankRegionAndNotesIntoNull() {
        service.update(command("  Sparky  ", "   ", "\n\t ", List.of(), 3L));

        LocalPokemon saved = savedPokemon();
        assertThat(saved.localName()).isEqualTo("Sparky");
        assertThat(saved.region()).isNull();
        assertThat(saved.notes()).isNull();
    }

    @Test
    void acceptsTextExactlyAtTheLimitsAfterTrimming() {
        service.update(command(" " + "n".repeat(100) + " ", "r".repeat(100), "x".repeat(1000), List.of(), 3L));

        LocalPokemon saved = savedPokemon();
        assertThat(saved.localName()).hasSize(100);
        assertThat(saved.region()).hasSize(100);
        assertThat(saved.notes()).hasSize(1000);
    }

    // --- text rules -------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    void rejectsABlankLocalNameThatIsPresent(String blank) {
        assertInvalid(command(blank, null, null, null, 3L), "localName");
    }

    @Test
    void rejectsTextOverTheLimits() {
        assertInvalid(command("n".repeat(101), null, null, null, 3L), "localName");
        assertInvalid(command(null, "r".repeat(101), null, null, 3L), "region");
        assertInvalid(command(null, null, "x".repeat(1001), null, 3L), "notes");
    }

    // --- tags -------------------------------------------------------------------------------------------------

    @Test
    void normalisesTagsByTrimmingAndLowercasingAndDropsDuplicatesSilently() {
        service.update(command(null, null, null, List.of(" Electric ", "electric", "STARTER", "fan-favourite-2"), 3L));

        assertThat(savedPokemon().tags()).containsExactly("electric", "fan-favourite-2", "starter");
    }

    @Test
    void acceptsTenDistinctTagsAndElevenWhenDuplicatesCollapseToTen() {
        List<String> ten = IntStream.range(0, 10).mapToObj(i -> "tag-" + i).toList();
        service.update(command(null, null, null, ten, 3L));
        assertThat(savedPokemon().tags()).hasSize(10);

        List<String> elevenWithDuplicate = new ArrayList<>(ten);
        elevenWithDuplicate.add("TAG-0");
        service.update(command(null, null, null, elevenWithDuplicate, 3L));
        assertThat(savedPokemon().tags()).hasSize(10);
    }

    @Test
    void rejectsMoreThanTenDistinctTags() {
        List<String> eleven = IntStream.range(0, 11).mapToObj(i -> "tag-" + i).toList();

        assertInvalid(command(null, null, null, eleven, 3L), "tags");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "has space", "under_score", "-leading", "trailing-", "double--dash",
            "dot.ted", "ünï", "emoji-😀"})
    void rejectsTagsThatAreNotLowercaseKebab(String tag) {
        assertInvalid(command(null, null, null, List.of("ok", tag), 3L), "tags[1]");
    }

    @Test
    void acceptsATagOfFiftyCharactersAndRejectsOneOfFiftyOne() {
        service.update(command(null, null, null, List.of("a".repeat(50)), 3L));
        assertThat(savedPokemon().tags()).containsExactly("a".repeat(50));

        assertInvalid(command(null, null, null, List.of("a".repeat(51)), 3L), "tags[0]");
    }

    @Test
    void rejectsANullTagWithItsIndex() {
        assertInvalid(command(null, null, null, Arrays.asList("ok", null), 3L), "tags[1]");
    }

    // --- id, version, lookup ------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsIdsBelowOne(int id) {
        assertThatThrownBy(() -> service.update(new UpdateLocalPokemonCommand(id, null, null, null, null, 0L)))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(e -> ((InvalidRequestException) e).field())
                .isEqualTo("id");

        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @NullSource
    void requiresAVersion(Long version) {
        assertInvalid(command(null, null, null, null, version), "version");
    }

    @Test
    void rejectsANegativeVersion() {
        assertInvalid(command(null, null, null, null, -1L), "version");
    }

    @Test
    void reportsAnUnknownRecordAsNotFound() {
        when(repository.findById(26)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(new UpdateLocalPokemonCommand(26, "x", null, null, null, 0L)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void reportsAStaleVersionAsAConflictAndDoesNotWrite() {
        assertThatThrownBy(() -> service.update(command("Sparky", null, null, null, 2L)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Pokémon 25 was modified by someone else (current version 3); reload and try again");

        verify(repository, never()).save(any());
    }

    @Test
    void reportsAVersionFromTheFutureAsAConflictToo() {
        assertThatThrownBy(() -> service.update(command("Sparky", null, null, null, 4L)))
                .isInstanceOf(ConflictException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void letsAConflictRaisedByTheStoreEscape() {
        when(repository.save(any())).thenThrow(new ConflictException("modified concurrently"));

        assertThatThrownBy(() -> service.update(command("Sparky", null, null, null, 3L)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("modified concurrently");
    }

    @Test
    void validatesTheInputBeforeTouchingTheStore() {
        assertInvalid(command("", null, null, null, 3L), "localName");

        verifyNoInteractions(repository);
    }

    // --- helpers ----------------------------------------------------------------------------------------------

    private void assertInvalid(UpdateLocalPokemonCommand command, String field) {
        assertThatThrownBy(() -> service.update(command))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(e -> ((InvalidRequestException) e).field())
                .isEqualTo(field);
        verify(repository, never()).save(any());
    }

    private LocalPokemon savedPokemon() {
        ArgumentCaptor<LocalPokemon> captor = ArgumentCaptor.forClass(LocalPokemon.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private static UpdateLocalPokemonCommand command(
            String localName, String region, String notes, List<String> tags, Long version) {
        return new UpdateLocalPokemonCommand(25, localName, region, notes, tags, version);
    }

    private static LocalPokemon stored() {
        return new LocalPokemon(25, "pikachu", "Mouse Pokémon", 60, 4, "https://img/25.png",
                "https://img/artwork/25.png", List.of("static", "lightning-rod"),
                "Old name", "Old region", "old notes", List.of("old"), SYNCED, SYNCED, 3L);
    }
}
