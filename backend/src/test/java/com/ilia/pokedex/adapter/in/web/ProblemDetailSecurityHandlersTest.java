package com.ilia.pokedex.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemDetailSecurityHandlersTest {

    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
    private final ProblemDetailAuthenticationEntryPoint entryPoint = new ProblemDetailAuthenticationEntryPoint(mapper);
    private final ProblemDetailAccessDeniedHandler accessDenied = new ProblemDetailAccessDeniedHandler(mapper);

    @Test
    void answers401WithAProblemDetailAndABearerChallengeWhenNoCredentialsWereSent() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(new MockHttpServletRequest("GET", "/api/v1/local-pokemon"), response,
                new InsufficientAuthenticationException("Full authentication is required"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        JsonNode body = mapper.readTree(response.getContentAsByteArray());
        assertThat(body.get("type").asText()).isEqualTo("about:blank");
        assertThat(body.get("title").asText()).isEqualTo("Unauthorized");
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("detail").asText()).isEqualTo("Authentication is required to access this resource");
        assertThat(body.get("instance").asText()).isEqualTo("/api/v1/local-pokemon");
    }

    @Test
    void saysTheTokenIsInvalidWithoutLeakingTheParserMessage() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(new MockHttpServletRequest("GET", "/api/v1/auth/me"), response,
                new InvalidBearerTokenException("Jwt expired at 2026-10-17T10:00:00Z"));

        assertThat(response.getStatus()).isEqualTo(401);
        JsonNode body = mapper.readTree(response.getContentAsByteArray());
        assertThat(body.get("detail").asText()).isEqualTo("The access token is invalid or has expired");
        assertThat(response.getContentAsString()).doesNotContain("2026-10-17T10:00:00Z");
    }

    @Test
    void treatsAnyOtherAuthenticationFailureAsMissingAuthentication() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(new MockHttpServletRequest("GET", "/x"), response, new BadCredentialsException("secret detail"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).doesNotContain("secret detail");
    }

    @Test
    void answers403WithAProblemDetail() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDenied.handle(new MockHttpServletRequest("DELETE", "/api/v1/local-pokemon/25"), response,
                new AccessDeniedException("Access Denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        JsonNode body = mapper.readTree(response.getContentAsByteArray());
        assertThat(body.get("type").asText()).isEqualTo("about:blank");
        assertThat(body.get("title").asText()).isEqualTo("Forbidden");
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("detail").asText()).isEqualTo("You do not have permission to access this resource");
        assertThat(body.get("instance").asText()).isEqualTo("/api/v1/local-pokemon/25");
    }
}
