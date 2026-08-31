package br.com.dnafutsal.scraper.service;

import br.com.dnafutsal.scraper.exception.UpstreamAccessException;
import br.com.dnafutsal.scraper.fpfs.FpfsCatalogClient;
import br.com.dnafutsal.scraper.fpfs.dto.FpfsCategoryEventResponse;
import br.com.dnafutsal.scraper.fpfs.dto.FpfsCategoryResponse;
import br.com.dnafutsal.scraper.fpfs.dto.FpfsDivisionResponse;
import br.com.dnafutsal.scraper.fpfs.dto.FpfsTitleResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FpfsCatalogLoaderTest {

    private final FpfsCatalogClient client =
            mock(FpfsCatalogClient.class);

    private final FpfsCatalogLoader loader =
            new FpfsCatalogLoader(
                    client
            );

    @Test
    void selectsOnlyExactActivePaulistaTitle() {
        when(
                client.titles(2026)
        ).thenReturn(
                List.of(
                        title(
                                19,
                                "Copa Paulista",
                                "A"
                        ),
                        title(
                                24,
                                "Paulista Feminino",
                                "A"
                        ),
                        title(
                                25,
                                "COPA UNIÃO",
                                "A"
                        ),
                        title(
                                16,
                                " Paulista ",
                                "A"
                        )
                )
        );

        when(
                client.divisions(
                        2026,
                        16
                )
        ).thenReturn(
                List.of(
                        division(
                                3,
                                "A1",
                                "A"
                        )
                )
        );

        when(
                client.categoryEvents(
                        2026,
                        16,
                        3
                )
        ).thenReturn(
                List.of(
                        event(
                                917,
                                16,
                                3,
                                7,
                                2026,
                                "A",
                                7,
                                "Principal",
                                1,
                                "A"
                        )
                )
        );

        var result =
                loader.loadPaulista(
                        2026
                );

        assertThat(
                result.titleId()
        ).isEqualTo(16);

        assertThat(
                result.titleName()
        ).isEqualTo(
                "Paulista"
        );

        assertThat(
                result.divisions()
        ).hasSize(1);

        verify(client)
                .divisions(
                        2026,
                        16
                );

        verify(
                client,
                never()
        ).divisions(
                2026,
                19
        );

        verify(
                client,
                never()
        ).divisions(
                2026,
                24
        );

        verify(
                client,
                never()
        ).divisions(
                2026,
                25
        );
    }

    @Test
    void rejectsSimilarTitlesWhenPaulistaDoesNotExist() {
        when(
                client.titles(2026)
        ).thenReturn(
                List.of(
                        title(
                                19,
                                "Copa Paulista",
                                "A"
                        ),
                        title(
                                24,
                                "Paulista Feminino",
                                "A"
                        ),
                        title(
                                25,
                                "COPA UNIÃO",
                                "A"
                        )
                )
        );

        assertThatThrownBy(
                () -> loader.loadPaulista(
                        2026
                )
        )
                .isInstanceOf(
                        UpstreamAccessException.class
                )
                .hasMessageContaining(
                        "Paulista não foi encontrado"
                );
    }

    @Test
    void filtersInactiveAndInconsistentCatalogEntries() {
        when(
                client.titles(2026)
        ).thenReturn(
                List.of(
                        title(
                                16,
                                "Paulista",
                                "A"
                        )
                )
        );

        when(
                client.divisions(
                        2026,
                        16
                )
        ).thenReturn(
                List.of(
                        division(
                                3,
                                " A1 ",
                                "A"
                        ),
                        division(
                                4,
                                "A2",
                                "I"
                        ),
                        division(
                                5,
                                "A3",
                                "A"
                        )
                )
        );

        when(
                client.categoryEvents(
                        2026,
                        16,
                        3
                )
        ).thenReturn(
                List.of(
                        /*
                         * válido
                         */
                        event(
                                917,
                                16,
                                3,
                                7,
                                2026,
                                "A",
                                7,
                                " Principal ",
                                20,
                                "A"
                        ),

                        /*
                         * válido e deve aparecer primeiro
                         * pela ordem de execução.
                         */
                        event(
                                918,
                                16,
                                3,
                                8,
                                2026,
                                "A",
                                8,
                                "Sub-20",
                                10,
                                "A"
                        ),

                        /*
                         * duplicado: não duplica categoria.
                         */
                        event(
                                918,
                                16,
                                3,
                                8,
                                2026,
                                "A",
                                8,
                                "Sub-20",
                                10,
                                "A"
                        ),

                        /*
                         * evento inativo
                         */
                        event(
                                919,
                                16,
                                3,
                                9,
                                2026,
                                "I",
                                9,
                                "Sub-18",
                                30,
                                "A"
                        ),

                        /*
                         * título incorreto
                         */
                        event(
                                920,
                                25,
                                3,
                                10,
                                2026,
                                "A",
                                10,
                                "Sub-16",
                                40,
                                "A"
                        ),

                        /*
                         * temporada incorreta
                         */
                        event(
                                921,
                                16,
                                3,
                                11,
                                2025,
                                "A",
                                11,
                                "Sub-14",
                                50,
                                "A"
                        ),

                        /*
                         * id_categoria externo diverge
                         * do objeto categoria.
                         */
                        event(
                                922,
                                16,
                                3,
                                99,
                                2026,
                                "A",
                                12,
                                "Sub-12",
                                60,
                                "A"
                        ),

                        /*
                         * eventId inválido
                         */
                        event(
                                0,
                                16,
                                3,
                                13,
                                2026,
                                "A",
                                13,
                                "Sub-10",
                                70,
                                "A"
                        ),

                        /*
                         * categoria inativa
                         */
                        event(
                                923,
                                16,
                                3,
                                14,
                                2026,
                                "A",
                                14,
                                "Sub-8",
                                80,
                                "I"
                        )
                )
        );

        /*
         * A3 não possui nenhum evento aproveitável.
         */
        when(
                client.categoryEvents(
                        2026,
                        16,
                        5
                )
        ).thenReturn(
                List.of(
                        event(
                                0,
                                16,
                                5,
                                20,
                                2026,
                                "A",
                                20,
                                "Inválida",
                                1,
                                "A"
                        )
                )
        );

        var result =
                loader.loadPaulista(
                        2026
                );

        assertThat(
                result.divisions()
        ).hasSize(1);

        var division =
                result.divisions()
                        .get(0);

        assertThat(
                division.id()
        ).isEqualTo(3);

        assertThat(
                division.name()
        ).isEqualTo("A1");

        assertThat(
                division.categories()
        )
                .extracting(
                        category ->
                                category.name()
                )
                .containsExactly(
                        "Sub-20",
                        "Principal"
                );

        assertThat(
                division.categories()
        )
                .extracting(
                        category ->
                                category.eventId()
                )
                .containsExactly(
                        918L,
                        917L
                );
    }

    private FpfsTitleResponse title(
            long id,
            String name,
            String status
    ) {
        return new FpfsTitleResponse(
                id,
                name,
                status
        );
    }

    private FpfsDivisionResponse division(
            long id,
            String name,
            String status
    ) {
        return new FpfsDivisionResponse(
                id,
                name,
                status
        );
    }

    private FpfsCategoryEventResponse event(
            long eventId,
            long titleId,
            long divisionId,
            long categoryId,
            int season,
            String eventStatus,
            long nestedCategoryId,
            String categoryName,
            Integer order,
            String categoryStatus
    ) {
        return new FpfsCategoryEventResponse(
                eventId,
                titleId,
                divisionId,
                categoryId,
                season,
                eventStatus,
                new FpfsCategoryResponse(
                        nestedCategoryId,
                        categoryName,
                        order,
                        categoryStatus
                )
        );
    }
}