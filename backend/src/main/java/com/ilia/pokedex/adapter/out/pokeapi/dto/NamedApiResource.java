package com.ilia.pokedex.adapter.out.pokeapi.dto;

/** PokeAPI reference to another resource: {@code {"name": "...", "url": "..."}}. */
public record NamedApiResource(String name, String url) {

    /** Last path segment of the URL, e.g. {@code .../pokemon-species/25/} -> {@code 25}. */
    public String idFromUrl() {
        if (url == null || url.isBlank()) {
            return name;
        }
        String trimmed = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        return trimmed.substring(trimmed.lastIndexOf('/') + 1);
    }
}
