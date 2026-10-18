package com.ilia.pokedex.adapter.out.pokeapi;

import com.ilia.pokedex.application.port.out.PokemonCatalog.CatalogSlice;
import com.ilia.pokedex.domain.exception.ExternalServiceException;
import com.ilia.pokedex.domain.exception.ResourceNotFoundException;
import com.ilia.pokedex.domain.model.PokemonDetails;
import com.ilia.pokedex.domain.model.PokemonDetails.EvolutionNode;
import com.ilia.pokedex.domain.model.PokemonDetails.Stat;
import com.ilia.pokedex.domain.model.PokemonSnapshot;
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
                new PokemonSummary(1, "bulbasaur", "https://img.test/1.png", "https://img.test/artwork/1.png", "Seed Pokémon", 69,
                        java.util.List.of("overgrow", "chlorophyll")),
                new PokemonSummary(386, "deoxys-normal", "https://img.test/386.png", null, "DNA Pokémon", 608,
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

    @Test
    void buildsPikachuDetailsFromPokemonSpeciesAndEvolutionChain() {
        expectJson("/pokemon/pikachu", "pokemon-pikachu");
        expectJson("/pokemon-species/25", "species-25");
        expectJson("/evolution-chain/10", "evolution-chain-10");

        PokemonDetails details = adapter.findDetails("pikachu");

        server.verify();
        assertThat(details.id()).isEqualTo(25);
        assertThat(details.name()).isEqualTo("pikachu");
        assertThat(details.imageUrl()).isEqualTo("https://img.test/artwork/25.png");
        assertThat(details.heightM()).isEqualTo(0.4);
        assertThat(details.weightKg()).isEqualTo(6.0);
        assertThat(details.types()).containsExactly("electric");
        assertThat(details.stats()).containsExactly(
                new Stat("hp", 35), new Stat("attack", 55), new Stat("defense", 40),
                new Stat("special-attack", 50), new Stat("special-defense", 50), new Stat("speed", 90));
        assertThat(details.description()).isEqualTo(
                "When several of these POK\u00e9MON gather, their electricity could build and cause lightning storms.");
    }

    @Test
    void mapsALinearEvolutionChainToANestedTree() {
        expectJson("/pokemon/pikachu", "pokemon-pikachu");
        expectJson("/pokemon-species/25", "species-25");
        expectJson("/evolution-chain/10", "evolution-chain-10");

        EvolutionNode root = adapter.findDetails("pikachu").evolutionChain();

        assertThat(root).isEqualTo(new EvolutionNode(172, "pichu", null, null, java.util.List.of(
                new EvolutionNode(25, "pikachu", null, "level-up", java.util.List.of(
                        new EvolutionNode(26, "raichu", null, "use-item", java.util.List.of()))))));
    }

    @Test
    void keepsEveryBranchOfAForkedEvolutionChain() {
        expectJson("/pokemon/eevee", "pokemon-eevee");
        expectJson("/pokemon-species/133", "species-133");
        expectJson("/evolution-chain/67", "evolution-chain-67");

        EvolutionNode root = adapter.findDetails("eevee").evolutionChain();

        assertThat(root.name()).isEqualTo("eevee");
        assertThat(root.evolvesTo()).extracting(EvolutionNode::name)
                .containsExactly("vaporeon", "jolteon", "flareon", "umbreon");
        assertThat(root.evolvesTo()).extracting(EvolutionNode::id)
                .containsExactly(134, 135, 136, 197);
        assertThat(root.evolvesTo()).extracting(EvolutionNode::trigger)
                .containsExactly("use-item", "use-item", "use-item", "level-up");
    }

    @Test
    void returnsASingleNodeChainForPokemonWithoutEvolutionsAndFallsBackToTheFrontSprite() {
        expectJson("/pokemon/ditto", "pokemon-ditto");
        expectJson("/pokemon-species/132", "species-132");
        expectJson("/evolution-chain/66", "evolution-chain-66");

        PokemonDetails details = adapter.findDetails("ditto");

        // the official artwork is null in the fixture, so the plain front sprite is used
        assertThat(details.imageUrl()).isEqualTo("https://img.test/132.png");
        assertThat(details.evolutionChain())
                .isEqualTo(new EvolutionNode(132, "ditto", null, null, java.util.List.of()));
    }

    @Test
    void keepsTheDetailsUsableWhenTheSpeciesIsMissing() {
        expectJson("/pokemon/pikachu", "pokemon-pikachu");
        server.expect(requestTo(BASE_URL + "/pokemon-species/25")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        PokemonDetails details = adapter.findDetails("pikachu");

        assertThat(details.description()).isNull();
        assertThat(details.evolutionChain())
                .isEqualTo(new EvolutionNode(25, "pikachu", null, null, java.util.List.of()));
    }

    @Test
    void reportsUnknownPokemonAsNotFound() {
        server.expect(requestTo(BASE_URL + "/pokemon/missingno")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(BASE_URL + "/pokemon-species/missingno")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> adapter.findDetails("missingno"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resolvesASpeciesNameToItsDefaultVarietyWhenThereIsNoPokemonOfThatName() {
        server.expect(requestTo(BASE_URL + "/pokemon/mouse")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(BASE_URL + "/pokemon-species/mouse")).andRespond(withSuccess("""
                {"id":25,"name":"mouse","genera":[],"flavor_text_entries":[],
                 "varieties":[{"is_default":false,"pokemon":{"name":"mouse-other","url":"https://pokeapi.test/api/v2/pokemon/10080/"}},
                              {"is_default":true,"pokemon":{"name":"pikachu","url":"https://pokeapi.test/api/v2/pokemon/25/"}}]}
                """, MediaType.APPLICATION_JSON));
        expectJson("/pokemon/pikachu", "pokemon-pikachu");
        expectJson("/pokemon-species/25", "species-25");
        expectJson("/evolution-chain/10", "evolution-chain-10");

        PokemonDetails details = adapter.findDetails("mouse");

        server.verify();
        assertThat(details.name()).isEqualTo("pikachu");
        assertThat(details.speciesName()).isEqualTo("pikachu");
    }

    @Test
    void translatesEvolutionChainServerErrorsIntoExternalServiceException() {
        expectJson("/pokemon/pikachu", "pokemon-pikachu");
        expectJson("/pokemon-species/25", "species-25");
        server.expect(requestTo(BASE_URL + "/evolution-chain/10"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> adapter.findDetails("pikachu"))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void buildsTheSyncSnapshotWithCategorySpriteArtworkAndOrderedAbilities() {
        expectJson("/pokemon/bulbasaur", "pokemon-bulbasaur");
        expectJson("/pokemon-species/1", "species-1");

        PokemonSnapshot snapshot = adapter.findByIdOrName("bulbasaur");

        server.verify();
        assertThat(snapshot).isEqualTo(new PokemonSnapshot(1, "bulbasaur", "Seed Pokémon", 69, 7,
                "https://img.test/1.png", "https://img.test/artwork/1.png",
                java.util.List.of("overgrow", "chlorophyll")));
    }

    @Test
    void leavesTheSnapshotCategoryEmptyWhenTheSpeciesHasNoEnglishGenus() {
        expectJson("/pokemon/pikachu", "pokemon-pikachu");
        expectJson("/pokemon-species/25", "species-25");

        PokemonSnapshot snapshot = adapter.findByIdOrName("pikachu");

        assertThat(snapshot.id()).isEqualTo(25);
        assertThat(snapshot.category()).isNull();
        assertThat(snapshot.weightHectograms()).isEqualTo(60);
        assertThat(snapshot.heightDecimetres()).isEqualTo(4);
    }

    @Test
    void keepsTheSnapshotSpriteAndLeavesTheArtworkEmptyWhenThereIsNone() {
        expectJson("/pokemon/ditto", "pokemon-ditto");
        expectJson("/pokemon-species/132", "species-132");

        PokemonSnapshot snapshot = adapter.findByIdOrName("ditto");

        assertThat(snapshot.spriteUrl()).isEqualTo("https://img.test/132.png");
        assertThat(snapshot.artworkUrl()).isNull();
    }

    @Test
    void buildsTheSnapshotWithoutCategoryWhenTheSpeciesIsMissing() {
        expectJson("/pokemon/bulbasaur", "pokemon-bulbasaur");
        server.expect(requestTo(BASE_URL + "/pokemon-species/1")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(adapter.findByIdOrName("bulbasaur").category()).isNull();
    }

    @Test
    void reportsAnUnknownPokemonAsNotFoundWhenBuildingTheSnapshot() {
        server.expect(requestTo(BASE_URL + "/pokemon/missingno")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(BASE_URL + "/pokemon-species/missingno")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> adapter.findByIdOrName("missingno"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void translatesServerErrorsWhileBuildingTheSnapshotIntoExternalServiceException() {
        server.expect(requestTo(BASE_URL + "/pokemon/pikachu")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> adapter.findByIdOrName("pikachu"))
                .isInstanceOf(ExternalServiceException.class);
    }

    private void expectJson(String path, String fixture) {
        server.expect(requestTo(BASE_URL + path))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json(fixture), MediaType.APPLICATION_JSON));
    }
}
