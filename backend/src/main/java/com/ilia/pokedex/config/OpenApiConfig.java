package com.ilia.pokedex.config;

import com.ilia.pokedex.adapter.in.web.docs.ApiDocs;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata of the generated OpenAPI document ({@code /v3/api-docs}, Swagger UI at {@code /swagger-ui.html}).
 * Declares the bearer JWT scheme; the controllers opt in per operation with
 * {@code @SecurityRequirement(name = "bearerAuth")}, so public operations stay without a padlock.
 * The version comes from the build ({@code build-info}), with a fallback for runs without it.
 */
@Configuration
public class OpenApiConfig {

    private static final String FALLBACK_VERSION = "dev";

    @Bean
    OpenAPI pokedexOpenApi(ObjectProvider<BuildProperties> buildProperties) {
        BuildProperties build = buildProperties.getIfAvailable();
        return new OpenAPI()
                .info(new Info()
                        .title("Pokédex API")
                        .version(build != null ? build.getVersion() : FALLBACK_VERSION)
                        .description("Browse Pokémon from PokeAPI, keep a local copy with your own name, region, "
                                + "notes and tags, and edit it with optimistic locking. Errors are RFC 7807 "
                                + "problem+json. Log in with POST /api/v1/auth/login (demo user: demo / "
                                + "demo12345) and paste the token into Authorize."))
                .components(new Components().addSecuritySchemes(ApiDocs.BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT from POST /api/v1/auth/login, sent as Authorization: Bearer <token>")));
    }
}
