package com.ilia.pokedex.adapter.out.pokeapi;

import com.ilia.pokedex.adapter.out.pokeapi.dto.EvolutionChainResponse;
import com.ilia.pokedex.adapter.out.pokeapi.dto.NamedApiResource;
import com.ilia.pokedex.adapter.out.pokeapi.dto.PokemonListResponse;
import com.ilia.pokedex.adapter.out.pokeapi.dto.PokemonResponse;
import com.ilia.pokedex.adapter.out.pokeapi.dto.SpeciesResponse;
import com.ilia.pokedex.application.port.out.PokemonCatalog;
import com.ilia.pokedex.domain.exception.DomainException;
import com.ilia.pokedex.domain.exception.ExternalServiceException;
import com.ilia.pokedex.domain.exception.ResourceNotFoundException;
import com.ilia.pokedex.domain.model.PokemonDetails;
import com.ilia.pokedex.domain.model.PokemonDetails.EvolutionNode;
import com.ilia.pokedex.domain.model.PokemonDetails.Stat;
import com.ilia.pokedex.domain.model.PokemonSnapshot;
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
 * executor (virtual threads in production, a same-thread executor in tests). The detail view is a
 * dependent chain (Pokémon -> species -> evolution chain), so it runs sequentially; each step is cached.
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

    @Override
    public PokemonDetails findDetails(String idOrName) {
        PokemonResponse pokemon = client.fetchPokemon(idOrName);
        SpeciesResponse species = pokemon.species() == null ? null : speciesOrNull(pokemon.species());
        return new PokemonDetails(
                pokemon.id(),
                pokemon.name(),
                imageOf(pokemon),
                pokemon.height(),
                pokemon.weight(),
                typeNames(pokemon),
                statsOf(pokemon),
                species == null ? null : descriptionOf(species),
                evolutionOf(pokemon, species));
    }

    @Override
    public PokemonSnapshot findByIdOrName(String idOrName) {
        PokemonResponse pokemon = client.fetchPokemon(idOrName);
        return new PokemonSnapshot(
                pokemon.id(),
                pokemon.name(),
                categoryOf(pokemon),
                pokemon.weight(),
                pokemon.height(),
                spriteOf(pokemon),
                artworkOf(pokemon),
                abilityNames(pokemon));
    }

    private PokemonSummary toSummary(String name) {
        PokemonResponse pokemon = client.fetchPokemon(name);
        return new PokemonSummary(
                pokemon.id(),
                pokemon.name(),
                spriteOf(pokemon),
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

    /** Official artwork when present, otherwise the plain front sprite. */
    private static String imageOf(PokemonResponse pokemon) {
        String artwork = artworkOf(pokemon);
        return artwork != null ? artwork : spriteOf(pokemon);
    }

    /** The official artwork, or {@code null} when PokeAPI has none. */
    private static String artworkOf(PokemonResponse pokemon) {
        PokemonResponse.Sprites sprites = pokemon.sprites();
        if (sprites == null || sprites.other() == null || sprites.other().officialArtwork() == null) {
            return null;
        }
        String artwork = sprites.other().officialArtwork().frontDefault();
        return artwork == null || artwork.isBlank() ? null : artwork;
    }

    /** The plain front sprite, or {@code null} when PokeAPI has none. */
    private static String spriteOf(PokemonResponse pokemon) {
        return pokemon.sprites() == null ? null : pokemon.sprites().frontDefault();
    }

    private static List<String> typeNames(PokemonResponse pokemon) {
        if (pokemon.types() == null) {
            return List.of();
        }
        return pokemon.types().stream()
                .sorted(Comparator.comparingInt(PokemonResponse.TypeSlot::slot))
                .map(slot -> slot.type().name())
                .toList();
    }

    private static List<Stat> statsOf(PokemonResponse pokemon) {
        if (pokemon.stats() == null) {
            return List.of();
        }
        return pokemon.stats().stream()
                .map(slot -> new Stat(slot.stat().name(), slot.baseStat()))
                .toList();
    }

    /** A missing species only costs the description and the chain, not the whole detail view. */
    private SpeciesResponse speciesOrNull(NamedApiResource speciesRef) {
        try {
            return client.fetchSpecies(speciesRef.idFromUrl());
        } catch (ResourceNotFoundException e) {
            return null;
        }
    }

    /** First English Pokédex entry; line breaks and form feeds become single spaces. */
    private static String descriptionOf(SpeciesResponse species) {
        if (species.flavorTextEntries() == null) {
            return null;
        }
        return species.flavorTextEntries().stream()
                .filter(e -> e.language() != null && ENGLISH.equals(e.language().name()))
                .map(SpeciesResponse.FlavorText::flavorText)
                .filter(Objects::nonNull)
                .map(text -> text.replaceAll("\\s+", " ").trim())
                .findFirst()
                .orElse(null);
    }

    /**
     * The full tree from the base species. When the species or its chain is unavailable, the Pokémon
     * is shown as the only node, which is also what PokeAPI returns for species without evolutions.
     */
    private EvolutionNode evolutionOf(PokemonResponse pokemon, SpeciesResponse species) {
        if (species != null && species.evolutionChain() != null) {
            try {
                EvolutionChainResponse chain = client.fetchEvolutionChain(idOf(species.evolutionChain()));
                if (chain.chain() != null) {
                    return toNode(chain.chain());
                }
            } catch (ResourceNotFoundException e) {
                // fall through to the single-node chain
            }
        }
        NamedApiResource speciesRef = pokemon.species();
        return speciesRef == null
                ? new EvolutionNode(pokemon.id(), pokemon.name(), null, null, List.of())
                : new EvolutionNode(idOf(speciesRef), speciesRef.name(), null, null, List.of());
    }

    private static EvolutionNode toNode(EvolutionChainResponse.ChainLink link) {
        EvolutionChainResponse.EvolutionDetail detail =
                link.evolutionDetails() == null || link.evolutionDetails().isEmpty()
                        ? null
                        : link.evolutionDetails().get(0);
        List<EvolutionNode> children = link.evolvesTo() == null
                ? List.of()
                : link.evolvesTo().stream().map(PokeApiCatalogAdapter::toNode).toList();
        return new EvolutionNode(
                idOf(link.species()),
                link.species().name(),
                detail == null ? null : detail.minLevel(),
                detail == null || detail.trigger() == null ? null : detail.trigger().name(),
                children);
    }

    private static int idOf(NamedApiResource resource) {
        try {
            return Integer.parseInt(resource.idFromUrl());
        } catch (NumberFormatException e) {
            throw new ExternalServiceException("PokeAPI returned an unexpected resource URL: " + resource.url(), e);
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
