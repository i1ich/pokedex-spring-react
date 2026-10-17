package com.ilia.pokedex.application.port.out;

import com.ilia.pokedex.domain.model.PokemonSummary;

import java.util.List;

/** Read access to the external Pokémon catalogue (implemented by the PokeAPI adapter). */
public interface PokemonCatalog {

    /**
     * @param offset zero-based index of the first entry
     * @param limit  maximum number of entries to return
     * @return the entries plus the total size of the catalogue
     */
    CatalogSlice findSlice(int offset, int limit);

    record CatalogSlice(List<PokemonSummary> items, long totalCount) {
        public CatalogSlice {
            items = List.copyOf(items);
        }
    }
}
