package com.ilia.pokedex.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Answers 401 as {@code application/problem+json} with {@code WWW-Authenticate: Bearer}. A bearer token that
 * was sent but rejected (bad signature, expired, malformed) gets its own message; the underlying parser
 * message is not echoed.
 */
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String MISSING = "Authentication is required to access this resource";
    static final String INVALID_TOKEN = "The access token is invalid or has expired";

    private final ObjectMapper objectMapper;

    public ProblemDetailAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        String detail = ex instanceof OAuth2AuthenticationException ? INVALID_TOKEN : MISSING;
        ProblemDetailWriter.write(objectMapper, request, response, HttpStatus.UNAUTHORIZED, "Unauthorized", detail);
    }
}
