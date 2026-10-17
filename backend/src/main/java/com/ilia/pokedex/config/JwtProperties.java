package com.ilia.pokedex.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * JWT settings ({@code app.jwt.*}): the HS256 signing secret and the token lifetime. The secret must be at
 * least 32 bytes (256 bits, the HS256 minimum), otherwise the application refuses to start rather than sign
 * tokens with a weak key.
 *
 * @param secret shared secret, at least {@value #MIN_SECRET_BYTES} bytes in UTF-8
 * @param ttl    token lifetime, positive; one hour when not set
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration ttl) {

    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "app.jwt.secret (JWT_SECRET) must be set and at least " + MIN_SECRET_BYTES + " bytes long");
        }
        if (ttl == null) {
            ttl = Duration.ofHours(1);
        }
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("app.jwt.ttl must be positive");
        }
    }

    /** The HMAC key shared by the token encoder and decoder. */
    SecretKey signingKey() {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** Never print the secret. */
    @Override
    public String toString() {
        return "JwtProperties[secret=***, ttl=" + ttl + "]";
    }
}
