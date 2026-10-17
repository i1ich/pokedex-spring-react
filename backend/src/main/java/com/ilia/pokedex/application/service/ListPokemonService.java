package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.in.ListPokemonUseCase;
import com.ilia.pokedex.application.port.out.PokemonCatalog;
import com.ilia.pokedex.application.port.out.PokemonCatalog.CatalogSlice;
import com.ilia.pokedex.domain.exception.InvalidRequestException;
import com.ilia.pokedex.domain.model.PageResult;
import com.ilia.pokedex.domain.model.PokemonSummary;

import java.util.Objects;

/** US01: validates pagination and reads one page from the external catalogue. */
public class ListPokemonService implements ListPokemonUseCase {

    private final PokemonCatalog catalog;

    public ListPokemonService(PokemonCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @Override
    public PageResult<PokemonSummary> list(int page, int size) {
        validate(page, size);
        CatalogSlice slice = catalog.findSlice(page * size, size);
        return new PageResult<>(slice.items(), page, size, slice.totalCount());
    }

    private static void validate(int page, int size) {
        if (page < 0) {
            throw new InvalidRequestException("page", "page must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("size", "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new InvalidRequestException("page", "page is too large");
        }
    }
}
