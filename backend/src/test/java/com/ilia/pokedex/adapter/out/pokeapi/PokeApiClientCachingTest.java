package com.ilia.pokedex.adapter.out.pokeapi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static com.ilia.pokedex.adapter.out.pokeapi.PokeApiFixtures.BASE_URL;
import static com.ilia.pokedex.adapter.out.pokeapi.PokeApiFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Verifies the cache proxy really prevents repeated upstream calls. */
@SpringJUnitConfig(PokeApiClientCachingTest.Config.class)
class PokeApiClientCachingTest {

    @Autowired
    private PokeApiClient client;

    @Autowired
    private MockRestServiceServer server;

    @Autowired
    private CacheManager cacheManager;

    @AfterEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        server.reset();
    }

    @Test
    void callsPokeApiOnlyOnceForRepeatedLookups() {
        server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/pokemon/bulbasaur"))
                .andRespond(withSuccess(json("pokemon-bulbasaur"), MediaType.APPLICATION_JSON));

        var first = client.fetchPokemon("bulbasaur");
        var second = client.fetchPokemon("BULBASAUR");

        server.verify();
        assertThat(second).isEqualTo(first);
    }

    @Configuration
    @EnableCaching
    static class Config {

        private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);

        @Bean
        MockRestServiceServer server() {
            return MockRestServiceServer.bindTo(builder).build();
        }

        @Bean
        PokeApiClient pokeApiClient(MockRestServiceServer server) {
            return new PokeApiClient(builder.build());
        }

        @Bean
        CacheManager cacheManager() {
            return new CaffeineCacheManager();
        }
    }
}
