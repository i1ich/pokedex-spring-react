package com.ilia.pokedex.domain.exception;

/** The requested resource does not exist (mapped to HTTP 404). */
public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String resource, Object key) {
        super(resource + " '" + key + "' was not found");
    }
}
