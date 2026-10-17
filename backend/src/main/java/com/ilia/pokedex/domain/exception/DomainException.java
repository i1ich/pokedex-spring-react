package com.ilia.pokedex.domain.exception;

/** Base type for errors the core reports to the outside world. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
