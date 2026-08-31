package br.com.dnafutsal.scraper.service;

import br.com.dnafutsal.scraper.domain.EventMetadata;
import br.com.dnafutsal.scraper.domain.EventSearchCriteria;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FutsalScraperServiceTest {

    private final CachedEventDataService data =
            mock(CachedEventDataService.class);

    private final FpfsCatalogService catalog =
            mock(FpfsCatalogService.class);

    private final FutsalScraperService service =
            new FutsalScraperService(
                    data,
                    catalog
            );

    @Test
    void snapshotDelegatesToCachedDataService() {
        EventMetadata event =
                event();

        when(
                data.eventMetadata(917)
        ).thenReturn(event);

        when(
                data.standings(917)
        ).thenReturn(List.of());

        when(
                data.games(917)
        ).thenReturn(List.of());

        when(
                data.teams(917)
        ).thenReturn(List.of());

        when(
                data.scorers(
                        917,
                        false
                )
        ).thenReturn(List.of());

        var snapshot =
                service.snapshot(917);

        assertThat(
                snapshot.event()
        ).isEqualTo(event);

        verify(data)
                .eventMetadata(917);

        verify(data)
                .standings(917);

        verify(data)
                .games(917);

        verify(data)
                .teams(917);

        verify(data)
                .scorers(
                        917,
                        false
                );
    }

    @Test
    void searchUsesCachedMetadataService() {
        EventSearchCriteria criteria =
                new EventSearchCriteria(
                        2026,
                        null,
                        "A1",
                        "Principal"
                );

        when(
                catalog.searchEventIds(
                        criteria
                )
        ).thenReturn(
                List.of(917L)
        );

        when(
                data.eventMetadata(917)
        ).thenReturn(
                event()
        );

        var result =
                service.searchEvents(
                        criteria
                );

        assertThat(result)
                .hasSize(1);

        verify(data)
                .eventMetadata(917);
    }

    private EventMetadata event() {
        return new EventMetadata(
                917,
                "Campeonato Paulista",
                2026,
                "Principal",
                "A1",
                "https://eventos.admfutsal.com.br/evento/917"
        );
    }
}