package com.ilia.pokedex.config;

import com.ilia.pokedex.application.port.in.ListPokemonUseCase;
import com.ilia.pokedex.application.port.out.PokemonCatalog;
import com.ilia.pokedex.application.service.ListPokemonService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the use cases. The application layer has no Spring annotations; this class is the
 * single place where services are bound to their port implementations.
 */
@Configuration
public class UseCaseConfig {

    @Bean
    ListPokemonUseCase listPokemonUseCase(PokemonCatalog pokemonCatalog) {
        return new ListPokemonService(pokemonCatalog);
    }
}
