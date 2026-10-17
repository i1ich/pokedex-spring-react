package com.ilia.pokedex.domain.model;

import com.ilia.pokedex.domain.exception.InvalidRequestException;

/**
 * A validated pagination request. {@code page} is zero-based; the size is capped at {@value #MAX_SIZE}
 * to protect the upstream API and the database. Shared by every paged use case.
 *
 * @throws InvalidRequestException (field {@code page} or {@code size}) if the values are out of range
 */
public record PageQuery(int page, int size) {

    public static final int MAX_SIZE = 50;

    public PageQuery {
        if (page < 0) {
            throw new InvalidRequestException("page", "page must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidRequestException("size", "size must be between 1 and " + MAX_SIZE);
        }
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new InvalidRequestException("page", "page is too large");
        }
    }

    /** Zero-based index of the first element of the page; cannot overflow thanks to the check above. */
    public int offset() {
        return page * size;
    }
}
