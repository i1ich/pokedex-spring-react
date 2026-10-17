package com.ilia.pokedex.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/** STUB (red step). */
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    public ProblemDetailAuthenticationEntryPoint(ObjectMapper objectMapper) {
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) {
    }
}
