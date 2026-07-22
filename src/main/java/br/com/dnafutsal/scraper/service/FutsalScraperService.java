package br.com.dnafutsal.scraper.service;

import br.com.dnafutsal.scraper.browser.EventSearchBrowser;
import br.com.dnafutsal.scraper.config.ScraperProperties;
import br.com.dnafutsal.scraper.domain.EventMetadata;
import br.com.dnafutsal.scraper.domain.EventSearchCriteria;
import br.com.dnafutsal.scraper.domain.EventSnapshot;
import br.com.dnafutsal.scraper.domain.Game;
import br.com.dnafutsal.scraper.domain.Scorer;
import br.com.dnafutsal.scraper.domain.StandingRow;
import br.com.dnafutsal.scraper.domain.TeamDetails;
import br.com.dnafutsal.scraper.domain.TeamSummary;
import br.com.dnafutsal.scraper.http.PoliteHttpFetcher;
import br.com.dnafutsal.scraper.parser.EventMetadataParser;
import br.com.dnafutsal.scraper.parser.GamesParser;
import br.com.dnafutsal.scraper.parser.ScorersParser;
import br.com.dnafutsal.scraper.parser.StandingsParser;
import br.com.dnafutsal.scraper.parser.TeamDetailsParser;
import br.com.dnafutsal.scraper.parser.TeamsParser;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class FutsalScraperService {

    private static final Logger log = LoggerFactory.getLogger(FutsalScraperService.class);

    private final ScraperProperties properties;
    private final PoliteHttpFetcher fetcher;
    private final EventSearchBrowser searchBrowser;
    private final EventMetadataParser metadataParser;
    private final StandingsParser standingsParser;
    private final GamesParser gamesParser;
    private final TeamsParser teamsParser;
    private final TeamDetailsParser teamDetailsParser;
    private final ScorersParser scorersParser;

    public FutsalScraperService(
            ScraperProperties properties,
            PoliteHttpFetcher fetcher,
            EventSearchBrowser searchBrowser,
            EventMetadataParser metadataParser,
            StandingsParser standingsParser,
            GamesParser gamesParser,
            TeamsParser teamsParser,
            TeamDetailsParser teamDetailsParser,
            ScorersParser scorersParser
    ) {
        this.properties = properties;
        this.fetcher = fetcher;
        this.searchBrowser = searchBrowser;
        this.metadataParser = metadataParser;
        this.standingsParser = standingsParser;
        this.gamesParser = gamesParser;
        this.teamsParser = teamsParser;
        this.teamDetailsParser = teamDetailsParser;
        this.scorersParser = scorersParser;
    }

    @Cacheable(cacheNames = "event-search", key = "#criteria.toString()")
    public List<EventMetadata> searchEvents(EventSearchCriteria criteria) {
        List<Long> eventIds = searchBrowser.search(criteria);
        List<EventMetadata> result = new ArrayList<>();
        for (Long eventId : eventIds) {
            try {
                EventMetadata metadata = eventMetadata(eventId);
                if (matches(metadata, criteria)) {
                    result.add(metadata);
                }
            } catch (RuntimeException exception) {
                log.warn("Evento {} encontrado na busca, mas não pôde ser validado: {}", eventId, exception.getMessage());
            }
        }
        return List.copyOf(result);
    }

    @Cacheable(cacheNames = "event-metadata", key = "#eventId")
    public EventMetadata eventMetadata(long eventId) {
        String url = eventUrl(eventId, "");
        return metadataParser.parse(eventId, fetcher.getDocument(url), url);
    }

    @Cacheable(cacheNames = "standings", key = "#eventId")
    public List<StandingRow> standings(long eventId) {
        return List.copyOf(standingsParser.parse(fetcher.getDocument(eventUrl(eventId, ""))));
    }

    @Cacheable(cacheNames = "games", key = "#eventId")
    public List<Game> games(long eventId) {
        EventMetadata metadata = eventMetadata(eventId);
        return List.copyOf(gamesParser.parse(fetcher.getDocument(eventUrl(eventId, "/jogos")), metadata));
    }

    @Cacheable(cacheNames = "teams", key = "#eventId")
    public List<TeamSummary> teams(long eventId) {
        return List.copyOf(teamsParser.parse(eventId, fetcher.getDocument(eventUrl(eventId, "/equipes"))));
    }

    @Cacheable(cacheNames = "team-details", key = "#eventId + ':' + #teamId + ':' + #includePersonalData")
    public TeamDetails teamDetails(long eventId, long teamId, boolean includePersonalData) {
        boolean expose = includePersonalData && properties.exposePersonalData();
        TeamSummary summary = teams(eventId).stream()
                .filter(team -> team.teamId() == teamId)
                .findFirst()
                .orElse(null);
        String url = eventUrl(eventId, "/equipe/" + teamId);
        Document document = fetcher.getDocument(url);
        return teamDetailsParser.parse(
                eventId,
                teamId,
                document,
                url,
                expose,
                summary == null ? null : summary.name(),
                summary == null ? null : summary.logoUrl()
        );
    }

    @Cacheable(cacheNames = "scorers", key = "#eventId + ':' + #includePersonalData")
    public List<Scorer> scorers(long eventId, boolean includePersonalData) {
        List<Scorer> parsed = scorersParser.parse(fetcher.getDocument(eventUrl(eventId, "/artilharia")));
        boolean expose = includePersonalData && properties.exposePersonalData();
        if (expose) {
            return List.copyOf(parsed);
        }
        return parsed.stream()
                .map(scorer -> new Scorer(
                        scorer.phase(), null, null, scorer.team(), scorer.teamLogoUrl(), scorer.goals(), true
                ))
                .toList();
    }

    @Cacheable(cacheNames = "snapshot", key = "#eventId")
    public EventSnapshot snapshot(long eventId) {
        return new EventSnapshot(
                eventMetadata(eventId),
                standings(eventId),
                games(eventId),
                teams(eventId),
                scorers(eventId, false),
                Instant.now()
        );
    }

    private String eventUrl(long eventId, String suffix) {
        return properties.baseUrl() + "/evento/" + eventId + suffix;
    }

    private boolean matches(EventMetadata event, EventSearchCriteria criteria) {
        return (criteria.season() == null || event.season() == criteria.season())
                && containsNormalized(event.title(), criteria.title())
                && containsNormalized(event.division(), criteria.division())
                && containsNormalized(event.category(), criteria.category());
    }

    private boolean containsNormalized(String actual, String expected) {
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
