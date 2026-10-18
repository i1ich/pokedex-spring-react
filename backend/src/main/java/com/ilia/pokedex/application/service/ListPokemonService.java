package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.in.ListPokemonUseCase;
import com.ilia.pokedex.application.port.out.PokemonCatalog;
import com.ilia.pokedex.application.port.out.PokemonCatalog.CatalogSlice;
import com.ilia.pokedex.domain.exception.InvalidRequestException;
import com.ilia.pokedex.domain.model.PageQuery;
import com.ilia.pokedex.domain.model.PageResult;
import com.ilia.pokedex.domain.model.PokemonSummary;

import java.util.Locale;
import java.util.Objects;

/** US01: validates pagination and reads one page (optionally filtered by name) from the external catalogue. */
public class ListPokemonService implements ListPokemonUseCase {

    private final PokemonCatalog catalog;

    public ListPokemonService(PokemonCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @Override
    public PageResult<PokemonSummary> list(int page, int size) {
        PageQuery query = new PageQuery(page, size);
        CatalogSlice slice = catalog.findSlice(query.offset(), query.size());
        return new PageResult<>(slice.items(), page, size, slice.totalCount());
    }

    @Override
    public PageResult<PokemonSummary> search(String query, int page, int size) {
        String fragment = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (fragment.isEmpty()) {
            return list(page, size);
        }
        if (fragment.length() > MAX_QUERY_LENGTH) {
            throw new InvalidRequestException("q", "q must be at most " + MAX_QUERY_LENGTH + " characters");
        }
        PageQuery pageQuery = new PageQuery(page, size);
        CatalogSlice slice = catalog.searchByName(fragment, pageQuery.offset(), pageQuery.size());
        return new PageResult<>(slice.items(), page, size, slice.totalCount());
    }
}
