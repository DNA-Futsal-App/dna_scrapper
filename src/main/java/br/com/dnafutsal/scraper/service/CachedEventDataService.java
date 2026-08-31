package br.com.dnafutsal.scraper.service;

import br.com.dnafutsal.scraper.config.ScraperProperties;
import br.com.dnafutsal.scraper.domain.EventMetadata;
import br.com.dnafutsal.scraper.domain.Game;
import br.com.dnafutsal.scraper.domain.Scorer;
import br.com.dnafutsal.scraper.domain.StandingRow;
import br.com.dnafutsal.scraper.domain.TeamDetails;
import br.com.dnafutsal.scraper.domain.TeamSummary;
import br.com.dnafutsal.scraper.parser.EventMetadataParser;
import br.com.dnafutsal.scraper.parser.GamesParser;
import br.com.dnafutsal.scraper.parser.ScorersParser;
import br.com.dnafutsal.scraper.parser.StandingsParser;
import br.com.dnafutsal.scraper.parser.TeamDetailsParser;
import br.com.dnafutsal.scraper.parser.TeamsParser;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CachedEventDataService {

    private final ScraperProperties properties;
    private final EventSourceService source;

    private final EventMetadataParser metadataParser;
    private final StandingsParser standingsParser;
    private final GamesParser gamesParser;
    private final TeamsParser teamsParser;
    private final TeamDetailsParser teamDetailsParser;
    private final ScorersParser scorersParser;

    public CachedEventDataService(
            ScraperProperties properties,
            EventSourceService source,
            EventMetadataParser metadataParser,
            StandingsParser standingsParser,
            GamesParser gamesParser,
            TeamsParser teamsParser,
            TeamDetailsParser teamDetailsParser,
            ScorersParser scorersParser
    ) {
        this.properties = properties;
        this.source = source;
        this.metadataParser = metadataParser;
        this.standingsParser = standingsParser;
        this.gamesParser = gamesParser;
        this.teamsParser = teamsParser;
        this.teamDetailsParser = teamDetailsParser;
        this.scorersParser = scorersParser;
    }

    @Cacheable(
            cacheNames = "event-metadata",
            key = "#eventId",
            sync = true
    )
    public EventMetadata eventMetadata(
            long eventId
    ) {
        EventSourceService.Page page =
                source.basePage(eventId);

        return metadataParser.parse(
                eventId,
                page.document(),
                page.url()
        );
    }

    @Cacheable(
            cacheNames = "standings",
            key = "#eventId",
            sync = true
    )
    public List<StandingRow> standings(
            long eventId
    ) {
        EventSourceService.Page page =
                source.basePage(eventId);

        return List.copyOf(
                standingsParser.parse(
                        page.document()
                )
        );
    }

    @Cacheable(
            cacheNames = "games",
            key = "#eventId",
            sync = true
    )
    public List<Game> games(
            long eventId
    ) {
        EventSourceService.Page base =
                source.basePage(eventId);

        EventMetadata metadata =
                metadataParser.parse(
                        eventId,
                        base.document(),
                        base.url()
                );

        EventSourceService.Page games =
                source.gamesPage(eventId);

        return List.copyOf(
                gamesParser.parse(
                        games.document(),
                        metadata
                )
        );
    }

    @Cacheable(
            cacheNames = "teams",
            key = "#eventId",
            sync = true
    )
    public List<TeamSummary> teams(
            long eventId
    ) {
        EventSourceService.Page page =
                source.teamsPage(eventId);

        return List.copyOf(
                teamsParser.parse(
                        eventId,
                        page.document()
                )
        );
    }

    @Cacheable(
            cacheNames = "team-details",
            key = "#eventId + ':' + #teamId + ':' + #includePersonalData",
            sync = true
    )
    public TeamDetails teamDetails(
            long eventId,
            long teamId,
            boolean includePersonalData
    ) {
        boolean expose =
                includePersonalData
                        && properties.exposePersonalData();

        EventSourceService.Page teamsPage =
                source.teamsPage(eventId);

        TeamSummary summary =
                teamsParser.parse(
                                eventId,
                                teamsPage.document()
                        )
                        .stream()
                        .filter(team ->
                                team.teamId() == teamId
                        )
                        .findFirst()
                        .orElse(null);

        EventSourceService.Page detailsPage =
                source.teamDetailsPage(
                        eventId,
                        teamId
                );

        return teamDetailsParser.parse(
                eventId,
                teamId,
                detailsPage.document(),
                detailsPage.url(),
                expose,
                summary == null
                        ? null
                        : summary.name(),
                summary == null
                        ? null
                        : summary.logoUrl()
        );
    }

    @Cacheable(
            cacheNames = "scorers",
            key = "#eventId + ':' + #includePersonalData",
            sync = true
    )
    public List<Scorer> scorers(
            long eventId,
            boolean includePersonalData
    ) {
        EventSourceService.Page page =
                source.scorersPage(eventId);

        List<Scorer> parsed =
                scorersParser.parse(
                        page.document()
                );

        boolean expose =
                includePersonalData
                        && properties.exposePersonalData();

        if (expose) {
            return List.copyOf(parsed);
        }

        return parsed.stream()
                .map(scorer ->
                        new Scorer(
                                scorer.phase(),
                                null,
                                null,
                                scorer.team(),
                                scorer.teamLogoUrl(),
                                scorer.goals(),
                                true
                        )
                )
                .toList();
    }
}