package com.ilia.pokedex.acceptance.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * WireMock standing in for PokeAPI: started once per JVM on a free port. The Pokémon it "knows" are served from the
 * JSON fixtures in {@code src/test/resources/pokeapi}, the same ones the adapter tests use. Requests nobody stubbed
 * get WireMock's default 404, which is what PokeAPI answers for an unknown Pokémon.
 */
public final class FakePokeApi {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String FIXTURES = "/pokeapi/";
    private static final String FIXTURE_HOST = "https://pokeapi.test/api/v2";
    private static final WireMockServer SERVER = start();

    private FakePokeApi() {
    }

    private static WireMockServer start() {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        return server;
    }

    public static String baseUrl() {
        return "http://localhost:" + SERVER.port();
    }

    public static void reset() {
        SERVER.resetAll();
    }

    /**
     * PokeAPI knows this Pokémon: its resource (by name and by id), its species and its evolution chain, as far as
     * the fixtures have them. The fixture is {@code pokemon-<name>.json}.
     */
    public static void knowsPokemon(String name) {
        String pokemonJson = fixture("pokemon-" + name);
        JsonNode pokemon = parse(pokemonJson);
        respondWithJson("/pokemon/" + name, pokemonJson);
        respondWithJson("/pokemon/" + pokemon.get("id").asInt(), pokemonJson);

        String speciesId = lastSegment(pokemon.path("species").path("url").asText());
        if (speciesId.isEmpty() || !hasFixture("species-" + speciesId)) {
            return;
        }
        String speciesJson = fixture("species-" + speciesId);
        respondWithJson("/pokemon-species/" + speciesId, speciesJson);

        String chainId = lastSegment(parse(speciesJson).path("evolution_chain").path("url").asText());
        if (!chainId.isEmpty() && hasFixture("evolution-chain-" + chainId)) {
            respondWithJson("/evolution-chain/" + chainId, fixture("evolution-chain-" + chainId));
        }
    }

    /** PokeAPI answers {@code GET /pokemon?offset&limit} with these Pokémon out of {@code total}. */
    public static void listsPokemon(int total, int offset, int limit, List<String> names) {
        ObjectNode list = JSON.createObjectNode().put("count", total);
        ArrayNode results = list.putArray("results");
        for (String name : names) {
            knowsPokemon(name);
            int id = parse(fixture("pokemon-" + name)).get("id").asInt();
            results.addObject().put("name", name).put("url", FIXTURE_HOST + "/pokemon/" + id + "/");
        }
        respondWithJson("/pokemon?offset=" + offset + "&limit=" + limit, list.toString());
    }

    /** PokeAPI explicitly answers 404 for this Pokémon. */
    public static void doesNotKnowPokemon(String name) {
        SERVER.stubFor(get(urlEqualTo("/pokemon/" + name)).willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"detail\":\"Not found.\"}")));
    }

    /** Every request that no more specific stub matches answers with the given status. */
    public static void failEverythingWith(int status) {
        SERVER.stubFor(any(anyUrl()).atPriority(10).willReturn(aResponse().withStatus(status)));
    }

    /** Number of requests PokeAPI has received since the last reset. */
    public static int requestCount() {
        return SERVER.getAllServeEvents().size();
    }

    private static void respondWithJson(String path, String json) {
        SERVER.stubFor(get(urlEqualTo(path)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(json)));
    }

    private static boolean hasFixture(String name) {
        return FakePokeApi.class.getResource(FIXTURES + name + ".json") != null;
    }

    private static String fixture(String name) {
        try (InputStream in = FakePokeApi.class.getResourceAsStream(FIXTURES + name + ".json")) {
            if (in == null) {
                throw new IllegalArgumentException("No PokeAPI fixture named " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonNode parse(String json) {
        try {
            return JSON.readTree(json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code https://host/api/v2/pokemon-species/25/} gives {@code 25}. */
    private static String lastSegment(String url) {
        String trimmed = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        return trimmed.substring(trimmed.lastIndexOf('/') + 1);
    }
}
