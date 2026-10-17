package com.ilia.pokedex.acceptance;

import com.ilia.pokedex.PokedexApplication;
import com.ilia.pokedex.acceptance.support.FakePokeApi;
import com.ilia.pokedex.acceptance.support.ScenarioContext;
import io.cucumber.spring.CucumberContextConfiguration;
import io.cucumber.spring.ScenarioScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One Spring context for the whole run: the real application on a random port, a real PostgreSQL (Testcontainers,
 * started and stopped by Spring through the {@code @ServiceConnection} bean) and WireMock standing in for PokeAPI.
 * Scenarios isolate themselves in {@link Hooks}.
 */
@CucumberContextConfiguration
@SpringBootTest(classes = PokedexApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(CucumberSpringConfiguration.Infrastructure.class)
public class CucumberSpringConfiguration {

    @DynamicPropertySource
    static void pokeApi(DynamicPropertyRegistry registry) {
        registry.add("pokeapi.base-url", FakePokeApi::baseUrl);
    }

    /** Only imported here, so the other integration tests never see these beans. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Infrastructure {

        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16-alpine");
        }

        /** Shared state of one scenario (last response, token, ...), shared by all step classes. */
        @Bean
        @ScenarioScope
        ScenarioContext scenarioContext(@Value("${local.server.port}") int port) {
            return new ScenarioContext(port);
        }
    }
}
