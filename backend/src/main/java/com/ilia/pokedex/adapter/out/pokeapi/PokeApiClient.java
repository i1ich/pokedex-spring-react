package com.ilia.pokedex.adapter.out.pokeapi;

import com.ilia.pokedex.adapter.out.pokeapi.dto.EvolutionChainResponse;
import com.ilia.pokedex.adapter.out.pokeapi.dto.PokemonListResponse;
import com.ilia.pokedex.adapter.out.pokeapi.dto.PokemonResponse;
import com.ilia.pokedex.adapter.out.pokeapi.dto.SpeciesResponse;
import com.ilia.pokedex.domain.exception.ExternalServiceException;
import com.ilia.pokedex.domain.exception.ResourceNotFoundException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.function.Supplier;

/**
 * Thin HTTP client for PokeAPI. Each method is cached (Caffeine, see application.yml): PokeAPI data is
 * effectively static, so repeated page views are served from memory. Caching lives here, in a separate
 * bean from the mapping adapter, because Spring's {@code @Cacheable} proxy ignores self-invocation.
 */
public class PokeApiClient {

    public static final String LIST_CACHE = "pokeapi-list";
    public static final String POKEMON_CACHE = "pokeapi-pokemon";
    public static final String SPECIES_CACHE = "pokeapi-species";
    public static final String EVOLUTION_CACHE = "pokeapi-evolution";

    private final RestClient restClient;

    public PokeApiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Cacheable(cacheNames = LIST_CACHE, key = "#offset + ':' + #limit")
    public PokemonListResponse fetchList(int offset, int limit) {
        return call("Pokemon list", offset + ":" + limit, () -> restClient.get()
                .uri("/pokemon?offset={offset}&limit={limit}", offset, limit)
                .retrieve()
                .body(PokemonListResponse.class));
    }

    @Cacheable(cacheNames = POKEMON_CACHE, key = "#idOrName.toLowerCase()")
    public PokemonResponse fetchPokemon(String idOrName) {
        return call("Pokemon", idOrName, () -> restClient.get()
                .uri("/pokemon/{idOrName}", idOrName.toLowerCase())
                .retrieve()
                .body(PokemonResponse.class));
    }

    @Cacheable(cacheNames = SPECIES_CACHE, key = "#idOrName.toLowerCase()")
    public SpeciesResponse fetchSpecies(String idOrName) {
        return call("Pokemon species", idOrName, () -> restClient.get()
                .uri("/pokemon-species/{idOrName}", idOrName.toLowerCase())
                .retrieve()
                .body(SpeciesResponse.class));
    }

    @Cacheable(cacheNames = EVOLUTION_CACHE, key = "#id")
    public EvolutionChainResponse fetchEvolutionChain(int id) {
        throw new UnsupportedOperationException("not implemented");
    }

    private static <T> T call(String resource, Object key, Supplier<T> request) {
        try {
            T body = request.get();
            if (body == null) {
                throw new ExternalServiceException("PokeAPI returned an empty body for " + resource + " " + key, null);
            }
            return body;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException(resource, key);
        } catch (RestClientException e) {
            throw new ExternalServiceException("PokeAPI request for " + resource + " '" + key + "' failed", e);
        }
    }
}
