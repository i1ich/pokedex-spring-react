package com.ilia.pokedex.domain.model;

import com.ilia.pokedex.domain.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageQueryTest {

    @Test
    void computesTheOffsetOfTheFirstElement() {
        assertThat(new PageQuery(2, 20).offset()).isEqualTo(40);
        assertThat(new PageQuery(0, 50).offset()).isZero();
    }

    @ParameterizedTest(name = "page={0}, size={1} -> invalid {2}")
    @CsvSource({
            "-1, 20, page",
            "0, 0, size",
            "0, -5, size",
            "0, 51, size"
    })
    void rejectsOutOfRangeValuesNamingTheField(int page, int size, String field) {
        assertThatThrownBy(() -> new PageQuery(page, size))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(e -> ((InvalidRequestException) e).field())
                .isEqualTo(field);
    }

    @Test
    void rejectsPagesWhoseOffsetWouldOverflow() {
        assertThatThrownBy(() -> new PageQuery(Integer.MAX_VALUE, 50))
                .isInstanceOf(InvalidRequestException.class);
    }
}
