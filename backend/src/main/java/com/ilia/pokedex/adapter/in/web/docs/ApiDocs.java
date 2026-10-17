package com.ilia.pokedex.adapter.in.web.docs;

/** Names shared by the OpenAPI annotations on the controllers and the OpenAPI configuration. */
public final class ApiDocs {

    /** Name of the HTTP bearer (JWT) security scheme declared in the OpenAPI document. */
    public static final String BEARER_AUTH = "bearerAuth";

    /** Content type of every error body ({@code ProblemDetail}, RFC 7807). */
    public static final String PROBLEM_JSON = "application/problem+json";

    public static final String TAG_POKEMON = "Pokédex (PokeAPI)";
    public static final String TAG_LOCAL_POKEMON = "Local Pokémon";
    public static final String TAG_AUTH = "Authentication";

    private ApiDocs() {
    }
}
