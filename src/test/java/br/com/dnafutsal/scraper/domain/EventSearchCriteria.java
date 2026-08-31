package br.com.dnafutsal.scraper.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventSearchCriteriaTest {

    @Test
    void acceptsDivisionAndCategoryWithoutTitle() {
        EventSearchCriteria criteria =
                new EventSearchCriteria(
                        2026,
                        null,
                        " A1 ",
                        " Principal "
                );

        assertThat(
                criteria.season()
        ).isEqualTo(2026);

        assertThat(
                criteria.title()
        ).isNull();

        assertThat(
                criteria.division()
        ).isEqualTo("A1");

        assertThat(
                criteria.category()
        ).isEqualTo(
                "Principal"
        );
    }

    @Test
    void trimsEmptyFiltersToNull() {
        EventSearchCriteria criteria =
                new EventSearchCriteria(
                        2026,
                        " ",
                        "",
                        "   "
                );

        assertThat(
                criteria.title()
        ).isNull();

        assertThat(
                criteria.division()
        ).isNull();

        assertThat(
                criteria.category()
        ).isNull();
    }

    @Test
    void stillRequiresSeason() {
        assertThatThrownBy(
                () -> new EventSearchCriteria(
                        null,
                        null,
                        null,
                        null
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "temporada"
                );
    }
}