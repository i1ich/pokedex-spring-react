package com.ilia.pokedex.adapter.out.pokeapi;

import com.ilia.pokedex.adapter.out.pokeapi.dto.NamedApiResource;
import com.ilia.pokedex.adapter.out.pokeapi.dto.PokemonListResponse;
import com.ilia.pokedex.adapter.out.pokeapi.dto.PokemonResponse;
import com.ilia.pokedex.adapter.out.pokeapi.dto.SpeciesResponse;
import com.ilia.pokedex.application.port.out.PokemonCatalog;
import com.ilia.pokedex.domain.exception.DomainException;
import com.ilia.pokedex.domain.exception.ExternalServiceException;
import com.ilia.pokedex.domain.exception.ResourceNotFoundException;
import com.ilia.pokedex.domain.model.PokemonSummary;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * Implements {@link PokemonCatalog} on top of PokeAPI. The list endpoint only returns names, so each
 * entry is enriched with its Pokémon and species resources. These lookups run in parallel on the given
 * executor (virtual threads in production, a same-thread executor in tests).
 */
public class PokeApiCatalogAdapter implements PokemonCatalog {

    private static final String ENGLISH = "en";

    private final PokeApiClient client;
    private final Executor executor;

    public PokeApiCatalogAdapter(PokeApiClient client, Executor executor) {
        this.client = Objects.requireNonNull(client, "client");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public CatalogSlice findSlice(int offset, int limit) {
        PokemonListResponse list = client.fetchList(offset, limit);
        List<NamedApiResource> results = list.results() == null ? List.of() : list.results();

        List<CompletableFuture<PokemonSummary>> futures = results.stream()
                .map(entry -> CompletableFuture.supplyAsync(() -> toSummary(entry.name()), executor))
                .toList();

        List<PokemonSummary> items = futures.stream().map(PokeApiCatalogAdapter::join).toList();
        return new CatalogSlice(items, list.count());
    }

    private PokemonSummary toSummary(String name) {
        PokemonResponse pokemon = client.fetchPokemon(name);
        return new PokemonSummary(
                pokemon.id(),
                pokemon.name(),
                pokemon.sprites() == null ? null : pokemon.sprites().frontDefault(),
                categoryOf(pokemon),
                pokemon.weight(),
                abilityNames(pokemon));
    }

    /** PokeAPI calls the category "genus", e.g. "Seed Pokémon". Missing species data is not fatal. */
    private String categoryOf(PokemonResponse pokemon) {
        if (pokemon.species() == null) {
            return null;
        }
        try {
            SpeciesResponse species = client.fetchSpecies(pokemon.species().idFromUrl());
            if (species.genera() == null) {
                return null;
            }
            return species.genera().stream()
                    .filter(g -> g.language() != null && ENGLISH.equals(g.language().name()))
                    .map(SpeciesResponse.Genus::genus)
                    .findFirst()
                    .orElse(null);
        } catch (ResourceNotFoundException e) {
            return null;
        }
    }

    private static List<String> abilityNames(PokemonResponse pokemon) {
        if (pokemon.abilities() == null) {
            return List.of();
        }
        return pokemon.abilities().stream()
                .sorted(Comparator.comparingInt(PokemonResponse.AbilitySlot::slot))
                .map(slot -> slot.ability().name())
                .toList();
    }

    private static PokemonSummary join(CompletableFuture<PokemonSummary> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof DomainException domain) {
                throw domain;
            }
            throw new ExternalServiceException("Failed to load Pokémon details", e.getCause());
        }
    }
}
