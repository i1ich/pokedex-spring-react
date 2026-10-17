package com.ilia.pokedex.adapter.out.pokeapi;

import com.ilia.pokedex.application.port.out.PokemonCatalog.CatalogSlice;
import com.ilia.pokedex.domain.exception.ExternalServiceException;
import com.ilia.pokedex.domain.model.PokemonSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static com.ilia.pokedex.adapter.out.pokeapi.PokeApiFixtures.BASE_URL;
import static com.ilia.pokedex.adapter.out.pokeapi.PokeApiFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PokeApiCatalogAdapterTest {

    private MockRestServiceServer server;
    private PokeApiCatalogAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        // Same-thread executor keeps request order deterministic for the mock server.
        adapter = new PokeApiCatalogAdapter(new PokeApiClient(builder.build()), Runnable::run);
    }

    @Test
    void enrichesEachListEntryWithSpriteCategoryWeightAndAbilities() {
        expectJson("/pokemon?offset=0&limit=2", "pokemon-list");
        expectJson("/pokemon/bulbasaur", "pokemon-bulbasaur");
        expectJson("/pokemon-species/1", "species-1");
        expectJson("/pokemon/deoxys-normal", "pokemon-deoxys-normal");
        expectJson("/pokemon-species/386", "species-386");

        CatalogSlice slice = adapter.findSlice(0, 2);

        server.verify();
        assertThat(slice.totalCount()).isEqualTo(1302);
        assertThat(slice.items()).containsExactly(
                new PokemonSummary(1, "bulbasaur", "https://img.test/1.png", "Seed Pokémon", 69,
                        java.util.List.of("overgrow", "chlorophyll")),
                new PokemonSummary(386, "deoxys-normal", "https://img.test/386.png", "DNA Pokémon", 608,
                        java.util.List.of("pressure")));
        assertThat(slice.items().get(0).weightKg()).isEqualTo(6.9);
    }

    @Test
    void resolvesSpeciesThroughTheSpeciesLinkNotThePokemonName() {
        expectJson("/pokemon?offset=0&limit=2", "pokemon-list");
        expectJson("/pokemon/bulbasaur", "pokemon-bulbasaur");
        expectJson("/pokemon-species/1", "species-1");
        expectJson("/pokemon/deoxys-normal", "pokemon-deoxys-normal");
        // "deoxys-normal" is a form; requesting /pokemon-species/deoxys-normal would 404.
        expectJson("/pokemon-species/386", "species-386");

        adapter.findSlice(0, 2);

        server.verify();
    }

    @Test
    void keepsTheEntryWithoutCategoryWhenSpeciesIsMissing() {
        expectJson("/pokemon?offset=0&limit=2", "pokemon-list");
        expectJson("/pokemon/bulbasaur", "pokemon-bulbasaur");
        server.expect(requestTo(BASE_URL + "/pokemon-species/1")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        expectJson("/pokemon/deoxys-normal", "pokemon-deoxys-normal");
        expectJson("/pokemon-species/386", "species-386");

        CatalogSlice slice = adapter.findSlice(0, 2);

        assertThat(slice.items().get(0).category()).isNull();
        assertThat(slice.items().get(1).category()).isEqualTo("DNA Pokémon");
    }

    @Test
    void translatesUpstreamServerErrorsIntoExternalServiceException() {
        server.expect(requestTo(BASE_URL + "/pokemon?offset=0&limit=2"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> adapter.findSlice(0, 2))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void translatesFailuresWhileEnrichingIntoExternalServiceException() {
        expectJson("/pokemon?offset=0&limit=2", "pokemon-list");
        server.expect(requestTo(BASE_URL + "/pokemon/bulbasaur"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> adapter.findSlice(0, 2))
                .isInstanceOf(ExternalServiceException.class);
    }

    private void expectJson(String path, String fixture) {
        server.expect(requestTo(BASE_URL + path))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json(fixture), MediaType.APPLICATION_JSON));
    }
}
