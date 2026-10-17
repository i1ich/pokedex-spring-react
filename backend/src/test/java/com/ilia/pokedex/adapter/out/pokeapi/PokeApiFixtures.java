package com.ilia.pokedex.adapter.out.pokeapi;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

final class PokeApiFixtures {

    static final String BASE_URL = "https://pokeapi.test/api/v2";

    private PokeApiFixtures() {
    }

    static String json(String name) {
        try (InputStream in = PokeApiFixtures.class.getResourceAsStream("/pokeapi/" + name + ".json")) {
            if (in == null) {
                throw new IllegalArgumentException("Missing fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
