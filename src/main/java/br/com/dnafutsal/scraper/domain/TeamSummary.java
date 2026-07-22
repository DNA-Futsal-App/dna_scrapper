package br.com.dnafutsal.scraper.domain;

public record TeamSummary(
        long teamId,
        String name,
        String logoUrl,
        String sourceUrl
) {
}
