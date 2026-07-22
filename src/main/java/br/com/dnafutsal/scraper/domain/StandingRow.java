package br.com.dnafutsal.scraper.domain;

public record StandingRow(
        String phase,
        String group,
        Integer position,
        String team,
        String logoUrl,
        Integer points,
        Integer games,
        Integer wins,
        Integer draws,
        Integer losses,
        Integer goalsFor,
        Integer goalsAgainst,
        Integer goalDifference,
        Double average,
        Double goalsForAverage,
        Double goalsAgainstAverage,
        Double technicalIndex
) {
}
