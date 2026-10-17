package com.ilia.pokedex.domain.model;

import java.util.List;
import java.util.function.Function;

/**
 * Framework-free page of results. {@code page} is zero-based.
 */
public record PageResult<T>(List<T> items, int page, int size, long totalElements) {

    public PageResult {
        items = List.copyOf(items);
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be >= 1");
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements must be >= 0");
        }
    }

    public int totalPages() {
        return (int) ((totalElements + size - 1) / size);
    }

    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        return new PageResult<>(items.stream().<R>map(mapper).toList(), page, size, totalElements);
    }
}
