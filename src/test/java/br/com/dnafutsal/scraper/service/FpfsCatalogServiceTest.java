package br.com.dnafutsal.scraper.service;

import br.com.dnafutsal.scraper.domain.EventSearchCriteria;
import br.com.dnafutsal.scraper.domain.SeasonCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FpfsCatalogServiceTest {

    private final FpfsCatalogLoader loader =
            mock(FpfsCatalogLoader.class);

    private final FpfsCatalogService service =
            new FpfsCatalogService(
                    loader
            );

    @Test
    void exposesRealDivisionIds() {
        when(
                loader.loadPaulista(
                        2026
                )
        ).thenReturn(
                catalog()
        );

        var result =
                service.divisions(
                        2026
                );

        assertThat(result)
                .extracting(
                        item ->
                                item.id()
                )
                .containsExactly(
                        3L,
                        4L
                );

        assertThat(result)
                .extracting(
                        item ->
                                item.name()
                )
                .containsExactly(
                        "A1",
                        "A2"
                );
    }

    @Test
    void exposesCategoryIdAndEventIdTogether() {
        when(
                loader.loadPaulista(
                        2026
                )
        ).thenReturn(
                catalog()
        );

        var result =
                service.categories(
                        2026,
                        3
                );

        assertThat(result)
                .hasSize(2);

        assertThat(
                result.get(0).id()
        ).isEqualTo(7);

        assertThat(
                result.get(0).eventId()
        ).isEqualTo(917);

        assertThat(
                result.get(1).id()
        ).isEqualTo(8);

        assertThat(
                result.get(1).eventId()
        ).isEqualTo(918);
    }

    @Test
    void searchesDivisionAndCategoryWithoutTitle() {
        when(
                loader.loadPaulista(
                        2026
                )
        ).thenReturn(
                catalog()
        );

        var result =
                service.searchEventIds(
                        new EventSearchCriteria(
                                2026,
                                null,
                                " a1 ",
                                " PRINCIPAL "
                        )
                );

        assertThat(result)
                .containsExactly(
                        917L
                );
    }

    @Test
    void categoryCanBeFilteredWithoutExplicitDivisionOrTitle() {
        when(
                loader.loadPaulista(
                        2026
                )
        ).thenReturn(
                catalog()
        );

        var result =
                service.searchEventIds(
                        new EventSearchCriteria(
                                2026,
                                null,
                                null,
                                "Principal"
                        )
                );

        assertThat(result)
                .containsExactly(
                        917L,
                        920L
                );
    }

    @Test
    void acceptsCampeonatoPaulistaAsTitleAlias() {
        when(
                loader.loadPaulista(
                        2026
                )
        ).thenReturn(
                catalog()
        );

        var result =
                service.searchEventIds(
                        new EventSearchCriteria(
                                2026,
                                "Campeonato Paulista",
                                null,
                                null
                        )
                );

        assertThat(result)
                .containsExactly(
                        917L,
                        918L,
                        920L
                );
    }

    @Test
    void rejectsCopaPaulista() {
        when(
                loader.loadPaulista(
                        2026
                )
        ).thenReturn(
                catalog()
        );

        var result =
                service.searchEventIds(
                        new EventSearchCriteria(
                                2026,
                                "Copa Paulista",
                                null,
                                null
                        )
                );

        assertThat(result)
                .isEmpty();
    }

    @Test
    void rejectsPaulistaFeminino() {
        when(
                loader.loadPaulista(
                        2026
                )
        ).thenReturn(
                catalog()
        );

        var result =
                service.searchEventIds(
                        new EventSearchCriteria(
                                2026,
                                "Paulista Feminino",
                                null,
                                null
                        )
                );

        assertThat(result)
                .isEmpty();
    }

    @Test
    void rejectsUnknownDivisionId() {
        when(
                loader.loadPaulista(
                        2026
                )
        ).thenReturn(
                catalog()
        );

        assertThatThrownBy(
                () -> service.categories(
                        2026,
                        999
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "Divisão não encontrada"
                );
    }

    @Test
    void removesDuplicateEventIdsFromSearchResult() {
        SeasonCatalog custom =
                new SeasonCatalog(
                        2026,
                        16,
                        "Paulista",
                        List.of(
                                new SeasonCatalog.Division(
                                        3,
                                        "A1",
                                        List.of(
                                                new SeasonCatalog.Category(
                                                        7,
                                                        "Principal",
                                                        1,
                                                        917
                                                ),
                                                new SeasonCatalog.Category(
                                                        8,
                                                        "Outra",
                                                        2,
                                                        917
                                                )
                                        )
                                )
                        )
                );

        when(
                loader.loadPaulista(
                        2026
                )
        ).thenReturn(
                custom
        );

        var result =
                service.searchEventIds(
                        new EventSearchCriteria(
                                2026,
                                null,
                                null,
                                null
                        )
                );

        assertThat(result)
                .containsExactly(
                        917L
                );
    }

    private SeasonCatalog catalog() {
        return new SeasonCatalog(
                2026,
                16,
                "Paulista",
                List.of(
                        new SeasonCatalog.Division(
                                3,
                                "A1",
                                List.of(
                                        new SeasonCatalog.Category(
                                                7,
                                                "Principal",
                                                1,
                                                917
                                        ),
                                        new SeasonCatalog.Category(
                                                8,
                                                "Sub-20",
                                                2,
                                                918
                                        )
                                )
                        ),

                        new SeasonCatalog.Division(
                                4,
                                "A2",
                                List.of(
                                        new SeasonCatalog.Category(
                                                7,
                                                "Principal",
                                                1,
                                                920
                                        )
                                )
                        )
                )
        );
    }
}