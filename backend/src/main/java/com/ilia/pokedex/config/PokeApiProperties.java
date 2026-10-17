package com.ilia.pokedex.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "pokeapi")
public record PokeApiProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {

    public PokeApiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://pokeapi.co/api/v2";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(10);
        }
    }
}
