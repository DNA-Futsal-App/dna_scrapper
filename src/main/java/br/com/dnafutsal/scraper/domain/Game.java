package br.com.dnafutsal.scraper.domain;

import java.time.LocalDate;
import java.time.LocalTime;

public record Game(
        Long gameId,
        String phase,
        LocalDate date,
        LocalTime time,
        String venue,
        String homeTeam,
        String homeLogoUrl,
        Integer homeScore,
        String awayTeam,
        String awayLogoUrl,
        Integer awayScore,
        boolean walkover,
        String matchSheetUrl
) {
}
