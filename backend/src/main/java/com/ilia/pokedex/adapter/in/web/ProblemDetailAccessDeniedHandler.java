package com.ilia.pokedex.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/** STUB (red step). */
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    public ProblemDetailAccessDeniedHandler(ObjectMapper objectMapper) {
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) {
    }
}
