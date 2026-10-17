package com.ilia.pokedex.config;

import com.ilia.pokedex.adapter.out.pokeapi.PokeApiCatalogAdapter;
import com.ilia.pokedex.adapter.out.pokeapi.PokeApiClient;
import com.ilia.pokedex.application.port.out.PokemonCatalog;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Wires the outbound PokeAPI adapter: HTTP client with timeouts, cache, parallel enrichment. */
@Configuration
@EnableCaching
public class PokeApiConfig {

    @Bean
    RestClient pokeApiRestClient(RestClient.Builder builder, PokeApiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    PokeApiClient pokeApiClient(RestClient pokeApiRestClient) {
        return new PokeApiClient(pokeApiRestClient);
    }

    @Bean(destroyMethod = "close")
    ExecutorService pokeApiExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    PokemonCatalog pokemonCatalog(PokeApiClient pokeApiClient, ExecutorService pokeApiExecutor) {
        return new PokeApiCatalogAdapter(pokeApiClient, pokeApiExecutor);
    }
}
