package com.ilia.pokedex.adapter.in.seed;

import com.ilia.pokedex.adapter.in.seed.DemoDataSeeder.SeedReport;
import com.ilia.pokedex.application.port.in.GetLocalPokemonUseCase;
import com.ilia.pokedex.application.port.in.SyncPokemonUseCase;
import com.ilia.pokedex.application.port.in.SyncPokemonUseCase.SyncResult;
import com.ilia.pokedex.domain.exception.ExternalServiceException;
import com.ilia.pokedex.domain.exception.ResourceNotFoundException;
import com.ilia.pokedex.domain.model.LocalPokemon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class DemoDataSeederTest {

    private GetLocalPokemonUseCase getLocalPokemon;
    private SyncPokemonUseCase syncPokemon;

    @BeforeEach
    void setUp() {
        getLocalPokemon = mock(GetLocalPokemonUseCase.class);
        syncPokemon = mock(SyncPokemonUseCase.class);
        when(getLocalPokemon.get(anyInt())).thenThrow(new ResourceNotFoundException("Local Pokémon", 0));
        when(syncPokemon.sync(anyString())).thenAnswer(call -> new SyncResult(stored(call.getArgument(0)), true));
    }

    private DemoDataSeeder seeder(List<Integer> ids) {
        return new DemoDataSeeder(getLocalPokemon, syncPokemon, ids, Runnable::run);
    }

    @Test
    void syncsEveryMissingPokemonInOrder() {
        SeedReport report = seeder(List.of(1, 2, 3)).seed();

        InOrder order = inOrder(syncPokemon);
        order.verify(syncPokemon).sync("1");
        order.verify(syncPokemon).sync("2");
        order.verify(syncPokemon).sync("3");
        assertThat(report.synced()).containsExactly(1, 2, 3);
        assertThat(report.skipped()).isEmpty();
        assertThat(report.failed()).isEmpty();
    }

    @Test
    void neverTouchesAPokemonThatIsAlreadyStored() {
        doReturn(stored("2")).when(getLocalPokemon).get(2);

        SeedReport report = seeder(List.of(1, 2, 3)).seed();

        // a re-sync would be harmless for proprietary fields today, but "already there" must mean "hands off"
        verify(syncPokemon, never()).sync("2");
        verify(syncPokemon).sync("1");
        verify(syncPokemon).sync("3");
        assertThat(report.skipped()).containsExactly(2);
        assertThat(report.synced()).containsExactly(1, 3);
    }

    @Test
    void doesNothingWhenEverythingIsStored() {
        doAnswer(call -> stored(String.valueOf(call.getArgument(0)))).when(getLocalPokemon).get(anyInt());

        SeedReport report = seeder(List.of(1, 2, 3)).seed();

        verifyNoInteractions(syncPokemon);
        assertThat(report.skipped()).containsExactly(1, 2, 3);
        assertThat(report.synced()).isEmpty();
    }

    @Test
    void secondPassIsANoOp() {
        List<Integer> stored = new ArrayList<>();
        doAnswer(call -> {
            int id = call.getArgument(0);
            if (stored.contains(id)) {
                return stored(String.valueOf(id));
            }
            throw new ResourceNotFoundException("Local Pokémon", id);
        }).when(getLocalPokemon).get(anyInt());
        doAnswer(call -> {
            stored.add(Integer.parseInt(call.getArgument(0)));
            return new SyncResult(stored(call.getArgument(0)), true);
        }).when(syncPokemon).sync(anyString());
        DemoDataSeeder seeder = seeder(List.of(1, 2));

        SeedReport first = seeder.seed();
        SeedReport second = seeder.seed();

        assertThat(first.synced()).containsExactly(1, 2);
        assertThat(second.synced()).isEmpty();
        assertThat(second.skipped()).containsExactly(1, 2);
        verify(syncPokemon).sync("1");
        verify(syncPokemon).sync("2");
    }

    @Test
    void keepsGoingAfterASyncFailureAndLogsAWarningForIt(CapturedOutput output) {
        doThrow(new ExternalServiceException("PokeAPI timed out", null)).when(syncPokemon).sync("2");

        SeedReport report = seeder(List.of(1, 2, 3)).seed();

        verify(syncPokemon).sync("3");
        assertThat(report.synced()).containsExactly(1, 3);
        assertThat(report.failed()).containsExactly(2);
        assertThat(output.getAll())
                .contains("WARN")
                .contains("Pokémon #2")
                .contains("PokeAPI timed out");
    }

    @Test
    void oneWarningPerFailedPokemonWhenPokeApiIsDown(CapturedOutput output) {
        doThrow(new ExternalServiceException("connection refused", null)).when(syncPokemon).sync(anyString());

        SeedReport report = seeder(List.of(1, 2, 3)).seed();

        assertThat(report.failed()).containsExactly(1, 2, 3);
        assertThat(report.synced()).isEmpty();
        assertThat(output.getAll().lines().filter(line -> line.contains("WARN") && line.contains("Pokémon #")))
                .hasSize(3);
    }

    @Test
    void anUnexpectedErrorOnOnePokemonDoesNotStopTheRest() {
        doThrow(new IllegalStateException("boom")).when(syncPokemon).sync("1");

        SeedReport report = seeder(List.of(1, 2)).seed();

        assertThat(report.failed()).containsExactly(1);
        assertThat(report.synced()).containsExactly(2);
    }

    @Test
    void aFailedLookupIsReportedAndNothingIsSyncedForIt(CapturedOutput output) {
        doThrow(new IllegalStateException("database is down")).when(getLocalPokemon).get(1);

        SeedReport report = seeder(List.of(1, 2)).seed();

        verify(syncPokemon, never()).sync("1");
        assertThat(report.failed()).containsExactly(1);
        assertThat(report.synced()).containsExactly(2);
        assertThat(output.getAll()).contains("WARN").contains("Pokémon #1").contains("database is down");
    }

    @Test
    void logsASummary(CapturedOutput output) {
        doReturn(stored("2")).when(getLocalPokemon).get(2);
        doThrow(new ExternalServiceException("nope", null)).when(syncPokemon).sync("3");

        seeder(List.of(1, 2, 3)).seed();

        assertThat(output.getAll()).contains("INFO").contains("1 synced").contains("1 already stored")
                .contains("1 failed");
    }

    @Test
    void runReturnsBeforeSeedingBecauseSeedingHappensOnTheExecutor() {
        List<Runnable> queued = new ArrayList<>();
        Executor deferred = queued::add;
        DemoDataSeeder seeder = new DemoDataSeeder(getLocalPokemon, syncPokemon, List.of(1, 2), deferred);

        seeder.run(new DefaultApplicationArguments());

        // startup is not held up: nothing was fetched yet, the work is waiting on the executor
        verifyNoInteractions(syncPokemon, getLocalPokemon);
        assertThat(queued).hasSize(1);

        queued.get(0).run();

        verify(syncPokemon).sync("1");
        verify(syncPokemon).sync("2");
    }

    @Test
    void exposesTheConfiguredIds() {
        assertThat(seeder(List.of(7, 8)).pokemonIds()).containsExactly(7, 8);
    }

    @Test
    void copiesTheIdListAndAcceptsAnEmptyOne() {
        List<Integer> ids = new ArrayList<>(List.of(1));
        DemoDataSeeder seeder = seeder(ids);
        ids.add(2);

        assertThat(seeder.pokemonIds()).containsExactly(1);
        assertThat(seeder(List.of()).seed().synced()).isEmpty();
    }

    @Test
    void rejectsAMissingIdList() {
        assertThatThrownBy(() -> new DemoDataSeeder(getLocalPokemon, syncPokemon, null, Runnable::run))
                .isInstanceOf(NullPointerException.class);
    }

    private static LocalPokemon stored(String id) {
        Instant now = Instant.parse("2026-10-17T10:00:00Z");
        return new LocalPokemon(Integer.parseInt(id), "pokemon-" + id, null, 10, 10, null, null, List.of(),
                null, null, null, List.of(), now, now, 0L);
    }
}
