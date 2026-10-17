package com.ilia.pokedex.config;

import com.ilia.pokedex.adapter.in.seed.DemoDataSeeder;
import com.ilia.pokedex.application.port.in.GetLocalPokemonUseCase;
import com.ilia.pokedex.application.port.in.SyncPokemonUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/**
 * Registers the {@link DemoDataSeeder} unless {@code app.seed.enabled=false}. The seeding runs on its own
 * short-lived daemon thread, so it never delays startup nor keeps the JVM alive. The executor is deliberately
 * not a bean: a public {@code Executor} bean would make Spring Boot back off from its own task executor.
 */
@Configuration
@EnableConfigurationProperties(SeedProperties.class)
public class SeedConfig {

    @Bean
    @ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
    DemoDataSeeder demoDataSeeder(
            GetLocalPokemonUseCase getLocalPokemon, SyncPokemonUseCase syncPokemon, SeedProperties properties) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("demo-seed-");
        executor.setDaemon(true);
        return new DemoDataSeeder(getLocalPokemon, syncPokemon, properties.pokemonIds(), executor);
    }
}
