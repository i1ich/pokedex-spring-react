package com.ilia.pokedex.acceptance.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * WireMock standing in for PokeAPI: started once per JVM on a free port. Requests nobody stubbed get WireMock's
 * default 404, which is what PokeAPI answers for an unknown Pokémon.
 */
public final class FakePokeApi {

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

    /** Answers {@code GET path} (path and query string, exactly) with the given JSON. */
    public static void respondWithJson(String path, String json) {
        SERVER.stubFor(get(urlEqualTo(path)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(json)));
    }

    /** Every request that no more specific stub matches answers with the given status. */
    public static void failEverythingWith(int status) {
        SERVER.stubFor(any(anyUrl()).atPriority(10).willReturn(aResponse().withStatus(status)));
    }

    /** Number of requests PokeAPI has received since the last reset. */
    public static int requestCount() {
        return SERVER.getAllServeEvents().size();
    }
}
