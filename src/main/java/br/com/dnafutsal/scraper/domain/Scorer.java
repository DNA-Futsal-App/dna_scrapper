package br.com.dnafutsal.scraper.domain;

public record Scorer(
        String phase,
        String player,
        String playerImageUrl,
        String team,
        String teamLogoUrl,
        Integer goals,
        boolean personalDataSuppressed
) {
}
