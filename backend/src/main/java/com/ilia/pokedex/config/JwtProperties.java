package com.ilia.pokedex.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** STUB (red step). */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration ttl) {
}
