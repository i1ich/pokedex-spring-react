package com.ilia.pokedex.domain.exception;

/** The request conflicts with the current state of a resource, e.g. a duplicate or a stale version (mapped to HTTP 409). */
public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(message);
    }
}
