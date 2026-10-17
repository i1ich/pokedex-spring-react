package com.ilia.pokedex.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Stub: the behaviour comes with the green commit. */
@ConfigurationProperties(prefix = "app.seed")
public record SeedProperties(List<Integer> pokemonIds) {
}
