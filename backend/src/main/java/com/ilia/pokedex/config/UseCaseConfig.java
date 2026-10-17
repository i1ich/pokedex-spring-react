package com.ilia.pokedex.config;

import com.ilia.pokedex.application.port.in.AuthenticateUserUseCase;
import com.ilia.pokedex.application.port.in.GetLocalPokemonUseCase;
import com.ilia.pokedex.application.port.in.GetPokemonDetailsUseCase;
import com.ilia.pokedex.application.port.in.ListLocalPokemonUseCase;
import com.ilia.pokedex.application.port.in.ListPokemonUseCase;
import com.ilia.pokedex.application.port.in.RegisterUserUseCase;
import com.ilia.pokedex.application.port.in.SyncPokemonUseCase;
import com.ilia.pokedex.application.port.out.LocalPokemonRepository;
import com.ilia.pokedex.application.port.out.PasswordHasher;
import com.ilia.pokedex.application.port.out.PokemonCatalog;
import com.ilia.pokedex.application.port.out.TokenIssuer;
import com.ilia.pokedex.application.port.out.UserRepository;
import com.ilia.pokedex.application.service.AuthenticateUserService;
import com.ilia.pokedex.application.service.GetLocalPokemonService;
import com.ilia.pokedex.application.service.GetPokemonDetailsService;
import com.ilia.pokedex.application.service.ListLocalPokemonService;
import com.ilia.pokedex.application.service.ListPokemonService;
import com.ilia.pokedex.application.service.RegisterUserService;
import com.ilia.pokedex.application.service.SyncPokemonService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

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

    @Bean
    GetPokemonDetailsUseCase getPokemonDetailsUseCase(PokemonCatalog pokemonCatalog) {
        return new GetPokemonDetailsService(pokemonCatalog);
    }

    @Bean
    SyncPokemonUseCase syncPokemonUseCase(
            PokemonCatalog pokemonCatalog, LocalPokemonRepository localPokemonRepository, Clock clock) {
        return new SyncPokemonService(pokemonCatalog, localPokemonRepository, clock);
    }

    @Bean
    ListLocalPokemonUseCase listLocalPokemonUseCase(LocalPokemonRepository localPokemonRepository) {
        return new ListLocalPokemonService(localPokemonRepository);
    }

    @Bean
    GetLocalPokemonUseCase getLocalPokemonUseCase(LocalPokemonRepository localPokemonRepository) {
        return new GetLocalPokemonService(localPokemonRepository);
    }

    @Bean
    RegisterUserUseCase registerUserUseCase(UserRepository userRepository, PasswordHasher passwordHasher, Clock clock) {
        return new RegisterUserService(userRepository, passwordHasher, clock);
    }

    @Bean
    AuthenticateUserUseCase authenticateUserUseCase(
            UserRepository userRepository, PasswordHasher passwordHasher, TokenIssuer tokenIssuer) {
        return new AuthenticateUserService(userRepository, passwordHasher, tokenIssuer);
    }
}
