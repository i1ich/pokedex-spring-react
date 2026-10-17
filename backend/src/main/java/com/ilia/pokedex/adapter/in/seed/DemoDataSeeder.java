package com.ilia.pokedex.adapter.in.seed;

import com.ilia.pokedex.application.port.in.GetLocalPokemonUseCase;
import com.ilia.pokedex.application.port.in.SyncPokemonUseCase;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.List;
import java.util.concurrent.Executor;

/** Stub: the behaviour comes with the green commit. */
public class DemoDataSeeder implements ApplicationRunner {

    public DemoDataSeeder(
            GetLocalPokemonUseCase getLocalPokemon,
            SyncPokemonUseCase syncPokemon,
            List<Integer> pokemonIds,
            Executor executor) {
    }

    @Override
    public void run(ApplicationArguments args) {
        throw new UnsupportedOperationException();
    }

    public SeedReport seed() {
        throw new UnsupportedOperationException();
    }

    public List<Integer> pokemonIds() {
        throw new UnsupportedOperationException();
    }

    /** Outcome of one seeding pass, by Pokémon id. */
    public record SeedReport(List<Integer> synced, List<Integer> skipped, List<Integer> failed) {
    }
}
