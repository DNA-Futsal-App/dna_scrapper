package br.com.dnafutsal.scraper.api;

import br.com.dnafutsal.scraper.domain.*;
import br.com.dnafutsal.scraper.service.FutsalScraperService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Year;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Validated
@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final FutsalScraperService service;

    public EventController(FutsalScraperService service) {
        this.service = service;
    }

    @GetMapping("/search")
    public List<EventMetadata> search(
            @RequestParam(required = false) @Min(2016) @Max(2100)  Integer season,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String division,
            @RequestParam(required = false) String category
    ) {
        int currentYear = Year.now().getValue();
        Integer dseason = season == null ? currentYear : season;
        return service.searchEvents(new EventSearchCriteria(dseason, title, division, category));
    }

    @GetMapping("/{eventId}")
    public EventMetadata event(@PathVariable @Positive long eventId) {
        return service.eventMetadata(eventId);
    }

    @GetMapping("/{eventId}/standings")
    public List<StandingRow> standings(
            @PathVariable @Positive long eventId,
            @RequestParam(required = false) String phase,
            @RequestParam(required = false) String group
    ) {
        return service.standings(eventId).stream()
                .filter(row -> contains(row.phase(), phase))
                .filter(row -> contains(row.group(), group))
                .toList();
    }

    @GetMapping("/{eventId}/games")
    public List<Game> games(
            @PathVariable @Positive long eventId,
            @RequestParam(required = false) String phase,
            @RequestParam(required = false) String team,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return service.games(eventId).stream()
                .filter(game -> contains(game.phase(), phase))
                .filter(game -> team == null || contains(game.homeTeam(), team) || contains(game.awayTeam(), team))
                .filter(game -> from == null || game.date() == null || !game.date().isBefore(from))
                .filter(game -> to == null || game.date() == null || !game.date().isAfter(to))
                .sorted(Comparator.comparing(Game::date, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Game::time, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @GetMapping("/{eventId}/teams")
    public List<TeamSummary> teams(@PathVariable @Positive long eventId) {
        return service.teams(eventId);
    }

    @GetMapping("/{eventId}/teams/{teamId}")
    public TeamDetails team(
            @PathVariable @Positive long eventId,
            @PathVariable @Positive long teamId,
            @RequestParam(defaultValue = "false") boolean includePersonalData
    ) {
        return service.teamDetails(eventId, teamId, includePersonalData);
    }

    @GetMapping("/{eventId}/scorers")
    public List<Scorer> scorers(
            @PathVariable @Positive long eventId,
            @RequestParam(required = false) String phase,
            @RequestParam(defaultValue = "10") @Min(1) @Max(500) int limit,
            @RequestParam(defaultValue = "true") boolean includePersonalData
    ) {
        return service.scorers(eventId, includePersonalData).stream()
                .filter(scorer -> contains(scorer.phase(), phase))
                .sorted(Comparator.comparing(Scorer::goals, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    @GetMapping("/{eventId}/snapshot")
    public EventSnapshot snapshot(@PathVariable @Positive long eventId) {
        return service.snapshot(eventId);
    }

    private boolean contains(String actual, String expected) {
        return expected == null || expected.isBlank() || normalize(actual).contains(normalize(expected));
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
