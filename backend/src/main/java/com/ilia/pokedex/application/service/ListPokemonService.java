package com.ilia.pokedex.application.service;

import com.ilia.pokedex.application.port.in.ListPokemonUseCase;
import com.ilia.pokedex.application.port.out.PokemonCatalog;
import com.ilia.pokedex.application.port.out.PokemonCatalog.CatalogSlice;
import com.ilia.pokedex.domain.model.PageQuery;
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
        PageQuery query = new PageQuery(page, size);
        CatalogSlice slice = catalog.findSlice(query.offset(), query.size());
        return new PageResult<>(slice.items(), page, size, slice.totalCount());
    }
}
