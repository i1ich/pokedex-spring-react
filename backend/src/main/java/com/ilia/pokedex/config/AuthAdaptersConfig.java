package com.ilia.pokedex.config;

import com.ilia.pokedex.adapter.out.security.BCryptPasswordHasher;
import com.ilia.pokedex.adapter.out.security.JwtTokenIssuer;
import com.ilia.pokedex.application.port.out.PasswordHasher;
import com.ilia.pokedex.application.port.out.TokenIssuer;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Clock;

/** Binds the security ports used by login and registration to their BCrypt and JWT implementations. */
@Configuration
public class AuthAdaptersConfig {

    @Bean
    PasswordHasher passwordHasher() {
        return new BCryptPasswordHasher();
    }

    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(properties.signingKey()));
    }

    @Bean
    TokenIssuer tokenIssuer(JwtEncoder jwtEncoder, Clock clock, JwtProperties properties) {
        return new JwtTokenIssuer(jwtEncoder, clock, properties.ttl());
    }
}
