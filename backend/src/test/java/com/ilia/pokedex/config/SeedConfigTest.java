package com.ilia.pokedex.config;

import com.ilia.pokedex.adapter.in.seed.DemoDataSeeder;
import com.ilia.pokedex.application.port.in.GetLocalPokemonUseCase;
import com.ilia.pokedex.application.port.in.SyncPokemonUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Wiring of the demo-data seeder, without a database: only the two use cases it needs are provided (as mocks),
 * and no application runner is executed by {@link ApplicationContextRunner}, so PokeAPI is never contacted.
 */
class SeedConfigTest {

    private final GetLocalPokemonUseCase getLocalPokemon = mock(GetLocalPokemonUseCase.class);
    private final SyncPokemonUseCase syncPokemon = mock(SyncPokemonUseCase.class);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SeedConfig.class)
            .withBean(GetLocalPokemonUseCase.class, () -> getLocalPokemon)
            .withBean(SyncPokemonUseCase.class, () -> syncPokemon);

    @Test
    void seederIsPresentByDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(DemoDataSeeder.class));
    }

    @Test
    void seederIsPresentWhenEnabledExplicitly() {
        runner.withPropertyValues("app.seed.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(DemoDataSeeder.class));
    }

    @Test
    void seederIsAbsentWhenDisabled() {
        runner.withPropertyValues("app.seed.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DemoDataSeeder.class));
    }

    @Test
    void creatingTheContextDoesNotSeed() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(DemoDataSeeder.class);
            verifyNoInteractions(getLocalPokemon, syncPokemon);
        });
    }

    @Test
    void seedsPokemonOneToTwelveByDefault() {
        runner.run(context -> assertThat(context.getBean(DemoDataSeeder.class).pokemonIds())
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 12).boxed().toList()));
    }

    @Test
    void theListOfIdsIsConfigurable() {
        runner.withPropertyValues("app.seed.pokemon-ids=25,1,133")
                .run(context -> assertThat(context.getBean(DemoDataSeeder.class).pokemonIds())
                        .containsExactly(25, 1, 133));
    }

    @Test
    void aBlankListMeansTheDefaultRange() {
        runner.withPropertyValues("app.seed.pokemon-ids=")
                .run(context -> assertThat(context.getBean(DemoDataSeeder.class).pokemonIds()).hasSize(12));
    }

    @Test
    void refusesToStartWithAnInvalidId() {
        runner.withPropertyValues("app.seed.pokemon-ids=1,0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void duplicateIdsCollapse() {
        assertThat(new SeedProperties(List.of(3, 1, 3)).pokemonIds()).containsExactly(3, 1);
    }

    @Test
    void rejectsIdsBelowOne() {
        assertThatThrownBy(() -> new SeedProperties(List.of(-5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.seed.pokemon-ids");
    }

    /**
     * The test classpath carries {@code config/application.yml}, which must switch seeding off while the main
     * {@code application.yml} still applies (a test {@code application.yml} would have replaced it).
     */
    @Test
    void testConfigurationDisablesSeedingAndKeepsTheMainConfiguration() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> {
                    var environment = context.getEnvironment();
                    assertThat(environment.getProperty("app.seed.enabled")).isEqualTo("false");
                    assertThat(environment.getProperty("spring.datasource.url")).contains("jdbc:postgresql");
                    assertThat(environment.getProperty("app.jwt.secret")).isNotBlank();
                    assertThat(environment.getProperty("spring.cache.cache-names")).contains("pokeapi-pokemon");
                    assertThat(environment.getProperty("pokeapi.base-url")).isNotBlank();
                });
    }

    @Test
    void seederIsAbsentUnderTheTestConfiguration() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> assertThat(context).doesNotHaveBean(DemoDataSeeder.class));
    }
}
