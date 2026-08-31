package br.com.dnafutsal.scraper.service;

import br.com.dnafutsal.scraper.domain.EventMetadata;
import br.com.dnafutsal.scraper.domain.EventSearchCriteria;
import br.com.dnafutsal.scraper.domain.EventSnapshot;
import br.com.dnafutsal.scraper.domain.Game;
import br.com.dnafutsal.scraper.domain.Scorer;
import br.com.dnafutsal.scraper.domain.StandingRow;
import br.com.dnafutsal.scraper.domain.TeamDetails;
import br.com.dnafutsal.scraper.domain.TeamSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class FutsalScraperService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    FutsalScraperService.class
            );

    private final CachedEventDataService data;
    private final FpfsCatalogService fpfsCatalogService;

    public FutsalScraperService(
            CachedEventDataService data,
            FpfsCatalogService fpfsCatalogService
    ) {
        this.data = data;
        this.fpfsCatalogService =
                fpfsCatalogService;
    }

    @Cacheable(
            cacheNames = "event-search",
            key = "#criteria.toString()",
            sync = true
    )
    public List<EventMetadata> searchEvents(
            EventSearchCriteria criteria
    ) {
        List<Long> eventIds =
                fpfsCatalogService
                        .searchEventIds(
                                criteria
                        );

        List<EventMetadata> result =
                new ArrayList<>();

        for (Long eventId : eventIds) {
            try {
                /*
                 * Chamada para outro bean.
                 * Portanto event-metadata cache funciona.
                 */
                EventMetadata metadata =
                        data.eventMetadata(
                                eventId
                        );

                if (
                        matches(
                                metadata,
                                criteria
                        )
                ) {
                    result.add(
                            metadata
                    );
                }

            } catch (RuntimeException exception) {
                log.warn(
                        "Evento {} encontrado na busca, mas não pôde ser validado: {}",
                        eventId,
                        exception.getMessage()
                );
            }
        }

        return List.copyOf(result);
    }

    public EventMetadata eventMetadata(
            long eventId
    ) {
        return data.eventMetadata(
                eventId
        );
    }

    public List<StandingRow> standings(
            long eventId
    ) {
        return data.standings(
                eventId
        );
    }

    public List<Game> games(
            long eventId
    ) {
        return data.games(
                eventId
        );
    }

    public List<TeamSummary> teams(
            long eventId
    ) {
        return data.teams(
                eventId
        );
    }

    public TeamDetails teamDetails(
            long eventId,
            long teamId,
            boolean includePersonalData
    ) {
        return data.teamDetails(
                eventId,
                teamId,
                includePersonalData
        );
    }

    public List<Scorer> scorers(
            long eventId,
            boolean includePersonalData
    ) {
        return data.scorers(
                eventId,
                includePersonalData
        );
    }

    @Cacheable(
            cacheNames = "snapshot",
            key = "#eventId",
            sync = true
    )
    public EventSnapshot snapshot(
            long eventId
    ) {
        /*
         * Todas estas chamadas atravessam
         * o proxy do CachedEventDataService.
         *
         * Portanto todos os caches internos
         * funcionam corretamente.
         */
        return new EventSnapshot(
                data.eventMetadata(
                        eventId
                ),
                data.standings(
                        eventId
                ),
                data.games(
                        eventId
                ),
                data.teams(
                        eventId
                ),
                data.scorers(
                        eventId,
                        false
                ),
                Instant.now()
        );
    }

    private boolean matches(
            EventMetadata event,
            EventSearchCriteria criteria
    ) {
        return (
                criteria.season() == null
                        || event.season()
                        == criteria.season()
        )
                && containsNormalized(
                event.title(),
                criteria.title()
        )
                && containsNormalized(
                event.division(),
                criteria.division()
        )
                && containsNormalized(
                event.category(),
                criteria.category()
        );
    }

    private boolean containsNormalized(
            String actual,
            String expected
    ) {
        return expected == null
                || expected.isBlank()
                || normalize(actual)
                .contains(
                        normalize(expected)
                );
    }

    private String normalize(
            String value
    ) {
        return Normalizer.normalize(
                        value == null
                                ? ""
                                : value,
                        Normalizer.Form.NFD
                )
                .replaceAll(
                        "\\p{M}",
                        ""
                )
                .toLowerCase(
                        Locale.ROOT
                )
                .replaceAll(
                        "[^\\p{L}\\p{N}]+",
                        " "
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }
}