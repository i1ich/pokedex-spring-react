package com.ilia.pokedex.adapter.in.seed;

import com.ilia.pokedex.application.port.in.GetLocalPokemonUseCase;
import com.ilia.pokedex.application.port.in.SyncPokemonUseCase;
import com.ilia.pokedex.domain.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Driving adapter that makes the demo useful on first start: it makes sure the configured Pokémon
 * (by default #1 to #12) are in the local store, using only the inbound ports.
 *
 * <ul>
 *   <li><b>Idempotent, never destructive:</b> a Pokémon that is already stored is left alone (not even
 *       re-synced), so edits to the proprietary fields survive restarts. Only missing ones are synced.</li>
 *   <li><b>Never blocks or breaks startup:</b> the work is handed to an executor, so the web server and the
 *       health endpoint are not held up by the PokeAPI round trips. A Pokémon that cannot be synced (PokeAPI
 *       down, timeout) is logged as a warning and the next one is tried; the next restart will retry it.</li>
 * </ul>
 */
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final GetLocalPokemonUseCase getLocalPokemon;
    private final SyncPokemonUseCase syncPokemon;
    private final List<Integer> pokemonIds;
    private final Executor executor;

    public DemoDataSeeder(
            GetLocalPokemonUseCase getLocalPokemon,
            SyncPokemonUseCase syncPokemon,
            List<Integer> pokemonIds,
            Executor executor) {
        this.getLocalPokemon = Objects.requireNonNull(getLocalPokemon, "getLocalPokemon");
        this.syncPokemon = Objects.requireNonNull(syncPokemon, "syncPokemon");
        this.pokemonIds = List.copyOf(Objects.requireNonNull(pokemonIds, "pokemonIds"));
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /** Schedules {@link #seed()} on the executor and returns immediately. */
    @Override
    public void run(ApplicationArguments args) {
        executor.execute(() -> {
            try {
                seed();
            } catch (RuntimeException e) {
                log.warn("Demo seed aborted: {}", e.toString());
            }
        });
    }

    /** One seeding pass over the configured ids, in order. A failure on one Pokémon never stops the others. */
    public SeedReport seed() {
        List<Integer> synced = new ArrayList<>();
        List<Integer> skipped = new ArrayList<>();
        List<Integer> failed = new ArrayList<>();
        for (int id : pokemonIds) {
            try {
                if (isStored(id)) {
                    skipped.add(id);
                } else {
                    syncPokemon.sync(String.valueOf(id));
                    synced.add(id);
                }
            } catch (RuntimeException e) {
                failed.add(id);
                log.warn("Demo seed: could not seed Pokémon #{}: {}", id, e.getMessage());
            }
        }
        log.info("Demo seed finished: {} synced, {} already stored, {} failed",
                synced.size(), skipped.size(), failed.size());
        return new SeedReport(synced, skipped, failed);
    }

    public List<Integer> pokemonIds() {
        return pokemonIds;
    }

    private boolean isStored(int id) {
        try {
            getLocalPokemon.get(id);
            return true;
        } catch (ResourceNotFoundException notStoredYet) {
            return false;
        }
    }

    /** Outcome of one seeding pass, by Pokémon id. */
    public record SeedReport(List<Integer> synced, List<Integer> skipped, List<Integer> failed) {

        public SeedReport {
            synced = List.copyOf(synced);
            skipped = List.copyOf(skipped);
            failed = List.copyOf(failed);
        }
    }
}
