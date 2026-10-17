package com.ilia.pokedex.domain.exception;

/** A request breaks a business rule (mapped to HTTP 400). */
public class InvalidRequestException extends DomainException {

    private final String field;

    public InvalidRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
