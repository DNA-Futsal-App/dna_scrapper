package br.com.dnafutsal.scraper.domain;

import java.time.Instant;
import java.util.List;

public record EventSnapshot(
        EventMetadata event,
        List<StandingRow> standings,
        List<Game> games,
        List<TeamSummary> teams,
        List<Scorer> scorers,
        Instant collectedAt
) {
}
