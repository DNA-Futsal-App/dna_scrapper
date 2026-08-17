package br.com.dnafutsal.scraper.domain;

import java.util.List;

public record CategoryTeams(
        String division,
        String category,
        List<EventTeams> events
) {
}
