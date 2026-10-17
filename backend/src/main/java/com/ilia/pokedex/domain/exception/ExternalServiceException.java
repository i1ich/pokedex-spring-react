package com.ilia.pokedex.domain.exception;

/** An upstream dependency (PokeAPI) failed or timed out (mapped to HTTP 502). */
public class ExternalServiceException extends DomainException {

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
