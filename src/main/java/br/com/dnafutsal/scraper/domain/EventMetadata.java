package br.com.dnafutsal.scraper.domain;

public record EventMetadata(
        long eventId,
        String title,
        int season,
        String category,
        String division,
        String sourceUrl
) {
}
