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
                        2026,
                        "Paulista"
                )
        ).thenReturn(
                maleCatalog()
        );

        var result =
                service.divisions(
                        2026,
                        "Paulista"
                );

        assertThat(result)
                .extracting(
                        item -> item.id()
                )
                .containsExactly(
                        3L,
                        4L
                );

        assertThat(result)
                .extracting(
                        item -> item.name()
                )
                .containsExactly(
                        "A1",
                        "A2"
                );
    }

    @Test
    void exposesFemaleDivisions() {
        when(
                loader.loadPaulista(
                        2026,
                        "Paulista Feminino"
                )
        ).thenReturn(
                femaleCatalog()
        );

        var result =
                service.divisions(
                        2026,
                        "Paulista Feminino"
                );

        assertThat(result)
                .extracting(
                        item -> item.id()
                )
                .containsExactly(
                        10L,
                        11L
                );

        assertThat(result)
                .extracting(
                        item -> item.name()
                )
                .containsExactly(
                        "A1 Feminino",
                        "A2 Feminino"
                );
    }

    @Test
    void exposesCategoryIdAndEventIdTogether() {
        when(
                loader.loadPaulista(
                        2026,
                        "Paulista"
                )
        ).thenReturn(
                maleCatalog()
        );

        var result =
                service.categories(
                        2026,
                        "Paulista",
                        3
                );

        assertThat(result)
                .hasSize(2);

        assertThat(
                result.get(0).id()
        ).isEqualTo(
                7
        );

        assertThat(
                result.get(0).eventId()
        ).isEqualTo(
                917
        );

        assertThat(
                result.get(1).id()
        ).isEqualTo(
                8
        );

        assertThat(
                result.get(1).eventId()
        ).isEqualTo(
                918
        );
    }

    @Test
    void exposesFemaleCategoryIdAndEventIdTogether() {
        when(
                loader.loadPaulista(
                        2026,
                        "Paulista Feminino"
                )
        ).thenReturn(
                femaleCatalog()
        );

        var result =
                service.categories(
                        2026,
                        "Paulista Feminino",
                        10
                );

        assertThat(result)
                .hasSize(2);

        assertThat(
                result.get(0).id()
        ).isEqualTo(
                20
        );

        assertThat(
                result.get(0).eventId()
        ).isEqualTo(
                926
        );

        assertThat(
                result.get(1).id()
        ).isEqualTo(
                21
        );

        assertThat(
                result.get(1).eventId()
        ).isEqualTo(
                927
        );
    }

    @Test
    void searchesDivisionAndCategoryWithoutTitle() {
        when(
                loader.loadPaulista(
                        2026,
                        "Paulista"
                )
        ).thenReturn(
                maleCatalog()
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
                        2026,
                        "Paulista"
                )
        ).thenReturn(
                maleCatalog()
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
                        2026,
                        "Campeonato Paulista"
                )
        ).thenReturn(
                maleCatalog()
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
    void acceptsPaulistaFeminino() {
        when(
                loader.loadPaulista(
                        2026,
                        "Paulista Feminino"
                )
        ).thenReturn(
                femaleCatalog()
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
                .containsExactly(
                        926L,
                        927L,
                        928L
                );
    }

    @Test
    void acceptsCampeonatoPaulistaFemininoAsTitleAlias() {
        when(
                loader.loadPaulista(
                        2026,
                        "Campeonato Paulista Feminino"
                )
        ).thenReturn(
                femaleCatalog()
        );

        var result =
                service.searchEventIds(
                        new EventSearchCriteria(
                                2026,
                                "Campeonato Paulista Feminino",
                                null,
                                null
                        )
                );

        assertThat(result)
                .containsExactly(
                        926L,
                        927L,
                        928L
                );
    }

    @Test
    void rejectsCopaPaulistaWhenReturnedCatalogDoesNotMatchRequestedTitle() {
        when(
                loader.loadPaulista(
                        2026,
                        "Copa Paulista"
                )
        ).thenReturn(
                maleCatalog()
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
    void filtersFemaleDivisionAndCategory() {
        when(
                loader.loadPaulista(
                        2026,
                        "Paulista Feminino"
                )
        ).thenReturn(
                femaleCatalog()
        );

        var result =
                service.searchEventIds(
                        new EventSearchCriteria(
                                2026,
                                "Paulista Feminino",
                                "A1 Feminino",
                                "Principal"
                        )
                );

        assertThat(result)
                .containsExactly(
                        926L
                );
    }

    @Test
    void rejectsUnknownDivisionId() {
        when(
                loader.loadPaulista(
                        2026,
                        "Paulista"
                )
        ).thenReturn(
                maleCatalog()
        );

        assertThatThrownBy(
                () ->
                        service.categories(
                                2026,
                                "Paulista",
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
    void rejectsUnknownFemaleDivisionId() {
        when(
                loader.loadPaulista(
                        2026,
                        "Paulista Feminino"
                )
        ).thenReturn(
                femaleCatalog()
        );

        assertThatThrownBy(
                () ->
                        service.categories(
                                2026,
                                "Paulista Feminino",
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
                        2026,
                        "Paulista"
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

    @Test
    void removesDuplicateFemaleEventIdsFromSearchResult() {
        SeasonCatalog custom =
                new SeasonCatalog(
                        2026,
                        24,
                        "Paulista Feminino",
                        List.of(
                                new SeasonCatalog.Division(
                                        10,
                                        "A1 Feminino",
                                        List.of(
                                                new SeasonCatalog.Category(
                                                        20,
                                                        "Principal",
                                                        1,
                                                        926
                                                ),
                                                new SeasonCatalog.Category(
                                                        21,
                                                        "Outra",
                                                        2,
                                                        926
                                                )
                                        )
                                )
                        )
                );

        when(
                loader.loadPaulista(
                        2026,
                        "Paulista Feminino"
                )
        ).thenReturn(
                custom
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
                .containsExactly(
                        926L
                );
    }

    private SeasonCatalog maleCatalog() {
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

    private SeasonCatalog femaleCatalog() {
        return new SeasonCatalog(
                2026,
                24,
                "Paulista Feminino",
                List.of(
                        new SeasonCatalog.Division(
                                10,
                                "A1 Feminino",
                                List.of(
                                        new SeasonCatalog.Category(
                                                20,
                                                "Principal",
                                                1,
                                                926
                                        ),
                                        new SeasonCatalog.Category(
                                                21,
                                                "Sub-20",
                                                2,
                                                927
                                        )
                                )
                        ),

                        new SeasonCatalog.Division(
                                11,
                                "A2 Feminino",
                                List.of(
                                        new SeasonCatalog.Category(
                                                20,
                                                "Principal",
                                                1,
                                                928
                                        )
                                )
                        )
                )
        );
    }
}