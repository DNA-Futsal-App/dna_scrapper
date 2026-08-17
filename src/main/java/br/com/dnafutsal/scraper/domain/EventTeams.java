package br.com.dnafutsal.scraper.domain;

import java.util.List;

public record EventTeams(
        EventMetadata event,
        List<TeamSummary> teams
) {
}
