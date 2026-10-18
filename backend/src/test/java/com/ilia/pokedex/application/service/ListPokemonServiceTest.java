package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.out.PokemonCatalog;
import com.ilia.pokedex.application.port.out.PokemonCatalog.CatalogSlice;
import com.ilia.pokedex.domain.exception.InvalidRequestException;
import com.ilia.pokedex.domain.model.PageResult;
import com.ilia.pokedex.domain.model.PokemonSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListPokemonServiceTest {

    private PokemonCatalog catalog;
    private ListPokemonService service;

    @BeforeEach
    void setUp() {
        catalog = mock(PokemonCatalog.class);
        service = new ListPokemonService(catalog);
    }

    @Test
    void translatesPageAndSizeIntoOffsetAndLimit() {
        when(catalog.findSlice(40, 20)).thenReturn(new CatalogSlice(List.of(pikachu()), 1302));

        PageResult<PokemonSummary> result = service.list(2, 20);

        verify(catalog).findSlice(40, 20);
        assertThat(result.items()).containsExactly(pikachu());
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(1302);
        assertThat(result.totalPages()).isEqualTo(66);
    }

    @Test
    void returnsEmptyPageWhenRequestedPageIsBeyondTheCatalogue() {
        when(catalog.findSlice(2000, 20)).thenReturn(new CatalogSlice(List.of(), 1302));

        PageResult<PokemonSummary> result = service.list(100, 20);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(1302);
    }

    @ParameterizedTest(name = "page={0}, size={1} -> invalid {2}")
    @CsvSource({
            "-1, 20, page",
            "0, 0, size",
            "0, -5, size",
            "0, 51, size"
    })
    void rejectsInvalidPaginationWithoutCallingTheCatalogue(int page, int size, String field) {
        assertThatThrownBy(() -> service.list(page, size))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(e -> ((InvalidRequestException) e).field())
                .isEqualTo(field);

        verify(catalog, never()).findSlice(anyInt(), anyInt());
    }

    @Test
    void rejectsPagesWhoseOffsetWouldOverflow() {
        assertThatThrownBy(() -> service.list(Integer.MAX_VALUE, 50))
                .isInstanceOf(InvalidRequestException.class);

        verify(catalog, never()).findSlice(anyInt(), anyInt());
    }

    @Test
    void acceptsTheMaximumPageSize() {
        when(catalog.findSlice(0, 50)).thenReturn(new CatalogSlice(List.of(), 0));

        assertThat(service.list(0, 50).size()).isEqualTo(50);
    }

    @Test
    void searchesByTheTrimmedLowercaseFragmentWithTheOffsetOfThePage() {
        when(catalog.searchByName("char", 20, 20)).thenReturn(new CatalogSlice(List.of(pikachu()), 45));

        PageResult<PokemonSummary> result = service.search("  ChAr ", 1, 20);

        verify(catalog).searchByName("char", 20, 20);
        assertThat(result.items()).containsExactly(pikachu());
        assertThat(result.totalElements()).isEqualTo(45);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void aBlankQueryListsTheWholeCatalogue(String query) {
        when(catalog.findSlice(0, 20)).thenReturn(new CatalogSlice(List.of(pikachu()), 1302));

        assertThat(service.search(query, 0, 20).totalElements()).isEqualTo(1302);

        verify(catalog, never()).searchByName(anyString(), anyInt(), anyInt());
    }

    @Test
    void rejectsAQueryLongerThanTheLimitWithoutCallingTheCatalogue() {
        assertThatThrownBy(() -> service.search("a".repeat(51), 0, 20))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(e -> ((InvalidRequestException) e).field())
                .isEqualTo("q");

        verify(catalog, never()).searchByName(anyString(), anyInt(), anyInt());
    }

    @Test
    void validatesPaginationOfASearchToo() {
        assertThatThrownBy(() -> service.search("pika", 0, 51))
                .isInstanceOf(InvalidRequestException.class);

        verify(catalog, never()).searchByName(anyString(), anyInt(), anyInt());
    }

    private static PokemonSummary pikachu() {
        return new PokemonSummary(25, "pikachu", "https://img/25.png", "https://img/artwork/25.png", "Mouse Pokémon", 60,
                List.of("static", "lightning-rod"));
    }
}
